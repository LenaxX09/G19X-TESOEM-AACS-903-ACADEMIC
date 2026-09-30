package com.cpp.captura;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity — Sprint 2 "Captura" del MVP CPP (pantalla de la sección 6.3).
 *
 * Flujo:
 *   1. Carga las órdenes de compra desde GET /api/ordenes.php.
 *   2. "Tomar foto": MediaStore.ACTION_IMAGE_CAPTURE + FileProvider (RF-M2).
 *   3. "Enviar": comprime la foto (JPEG 85, lado mayor ≤ 1600 px), la codifica
 *      en Base64 y hace POST /api/facturas.php { orden_id, imagen_base64 }
 *      con Volley (RF-M4, versión simplificada solo Base64).
 *   4. Sprint 4 (RF-B3): al recibir el id, llama a GET /api/procesar.php?id=N
 *      (OCR → riesgo → conciliación en el servidor) y muestra el veredicto:
 *      ✅ Aprobada / ⚠️ Con Discrepancia, con % de riesgo y duración.
 *   5. Botón "Ver facturas" → ListadoActivity con badges (RF-M5).
 *
 * Servidor: Laragon en el PC de desarrollo.
 *   - Emulador:     usar 10.0.2.2 (alias del host local).
 *   - Móvil físico: usar la IP LAN del PC (misma red WiFi).
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CPP_S2";
    private static final int REQ_CAMARA = 1001;
    private static final int REQ_PERMISO_CAMARA = 1002;
    private static final int MAX_LADO_ENVIO = 1600;    // px del lado mayor al enviar
    private static final int MAX_LADO_PREVIEW = 1000;  // px del lado mayor en pantalla
    private static final int TIMEOUT_MS = 30000;       // KPI-1: pipeline ≤ 30 s

    private RequestQueue cola;
    private SharedPreferences prefs;

    private EditText txtServidor;
    private Spinner spOrdenes;
    private ImageView imgPreview;
    private Button btnCargar;
    private Button btnFoto;
    private Button btnEnviar;
    private Button btnLista;
    private ProgressBar barraEnvio;
    private TextView txtResultado;

    private File archivoFoto;
    private final List<Orden> ordenes = new ArrayList<>();
    private int ordenIdSeleccionada = -1;

    /** Modelo mínimo de orden de compra (GET /api/ordenes.php → datos.ordenes[]). */
    private static class Orden {
        final int id;
        final String codigo;
        final String proveedor;
        final String monto;

        Orden(int id, String codigo, String proveedor, String monto) {
            this.id = id;
            this.codigo = codigo;
            this.proveedor = proveedor;
            this.monto = monto;
        }

        String etiqueta() {
            return codigo + " · " + proveedor + "  ($" + monto + ")";
        }
    }

    // ========================================================================
    // Ciclo de vida
    // ========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getPreferences(MODE_PRIVATE);
        cola = Volley.newRequestQueue(this);

        txtServidor = findViewById(R.id.txtServidor);
        spOrdenes = findViewById(R.id.spOrdenes);
        imgPreview = findViewById(R.id.imgPreview);
        btnCargar = findViewById(R.id.btnCargar);
        btnFoto = findViewById(R.id.btnFoto);
        btnEnviar = findViewById(R.id.btnEnviar);
        btnLista = findViewById(R.id.btnLista);
        barraEnvio = findViewById(R.id.barraEnvio);
        txtResultado = findViewById(R.id.txtResultado);

        // Servidor por defecto: alias del host local visto desde el emulador.
        txtServidor.setText(prefs.getString("servidor", "10.0.2.2"));

        btnCargar.setOnClickListener(v -> {
            guardarServidor();
            cargarOrdenes();
        });
        btnFoto.setOnClickListener(v -> pedirCamara());
        btnEnviar.setOnClickListener(v -> enviarFactura());
        btnLista.setOnClickListener(v -> abrirListado());

        spOrdenes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                ordenIdSeleccionada = (pos >= 0 && pos < ordenes.size()) ? ordenes.get(pos).id : -1;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                ordenIdSeleccionada = -1;
            }
        });

        cargarOrdenes();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (cola != null) {
            cola.cancelAll(TAG);
        }
    }

    // ========================================================================
    // Red — URL base y carga de órdenes (GET /api/ordenes.php)
    // ========================================================================

    private void guardarServidor() {
        prefs.edit().putString("servidor", host()).apply();
    }

    /** Limpia lo tecleado: admite "10.0.2.2", "http://192.168.1.5", "192.168.1.5/". */
    private String host() {
        String h = txtServidor.getText().toString().trim();
        if (h.startsWith("http://")) h = h.substring(7);
        if (h.startsWith("https://")) h = h.substring(8);
        while (h.endsWith("/")) h = h.substring(0, h.length() - 1);
        return h.isEmpty() ? "10.0.2.2" : h;
    }

    private String urlBase() {
        return "http://" + host() + "/cpp/api";
    }

    private void cargarOrdenes() {
        txtResultado.setTextColor(ContextCompat.getColor(this, R.color.texto_normal));
        txtResultado.setText("Consultando órdenes en " + urlBase() + "/ordenes.php …");

        JsonObjectRequest peticion = new JsonObjectRequest(Request.Method.GET,
                urlBase() + "/ordenes.php", null,
                response -> {
                    try {
                        JSONArray lista = response.getJSONObject("datos").getJSONArray("ordenes");
                        ordenes.clear();
                        for (int i = 0; i < lista.length(); i++) {
                            JSONObject o = lista.getJSONObject(i);
                            ordenes.add(new Orden(
                                    o.getInt("id"),
                                    o.optString("codigo", "OC-?"),
                                    o.optString("proveedor", "?"),
                                    o.optString("monto_autorizado", "0")));
                        }
                        List<String> etiquetas = new ArrayList<>();
                        for (Orden o : ordenes) {
                            etiquetas.add(o.etiqueta());
                        }
                        ArrayAdapter<String> adaptador = new ArrayAdapter<>(this,
                                android.R.layout.simple_spinner_item, etiquetas);
                        adaptador.setDropDownViewResource(
                                android.R.layout.simple_spinner_dropdown_item);
                        spOrdenes.setAdapter(adaptador);
                        txtResultado.setText("Órdenes cargadas: " + ordenes.size()
                                + ". Ya puedes tomar la foto.");
                    } catch (JSONException e) {
                        mostrarError("Respuesta inesperada del servidor: " + e.getMessage());
                    }
                },
                error -> mostrarError("No se pudo conectar con " + host()
                        + ". ¿Está Laragon iniciado? (" + mensajeVolley(error) + ")"));
        peticion.setTag(TAG);
        cola.add(peticion);
    }

    // ========================================================================
    // Cámara — RF-M2: MediaStore.ACTION_IMAGE_CAPTURE + FileProvider
    // ========================================================================

    private void pedirCamara() {
        if (checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            lanzarCamara();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERMISO_CAMARA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISO_CAMARA) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                lanzarCamara();
            } else {
                Toast.makeText(this, R.string.sin_permiso_camara, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void lanzarCamara() {
        archivoFoto = new File(getCacheDir(), "captura_factura.jpg");
        Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", archivoFoto);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQ_CAMARA);
        } else {
            Toast.makeText(this, R.string.sin_app_camara, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAMARA) {
            return;
        }
        if (resultCode != RESULT_OK || archivoFoto == null
                || !archivoFoto.exists() || archivoFoto.length() == 0) {
            txtResultado.setText(R.string.foto_cancelada);
            return;
        }
        Bitmap preview = decodificar(archivoFoto, MAX_LADO_PREVIEW);
        if (preview == null) {
            mostrarError("No se pudo leer la foto capturada.");
            return;
        }
        imgPreview.setImageBitmap(preview);
        btnEnviar.setEnabled(true);
        txtResultado.setText(R.string.foto_lista);
    }

    // ========================================================================
    // Envío — RF-M4 (Volley + Base64) → POST /api/facturas.php (RF-B1)
    // ========================================================================

    private void enviarFactura() {
        if (ordenIdSeleccionada < 1) {
            mostrarError(getString(R.string.falta_orden));
            return;
        }
        if (archivoFoto == null || !archivoFoto.exists()) {
            mostrarError(getString(R.string.falta_foto));
            return;
        }
        guardarServidor();

        byte[] bytes;
        try {
            bytes = comprimirParaEnvio();
        } catch (IOException e) {
            mostrarError("No se pudo preparar la foto: " + e.getMessage());
            return;
        }
        String imagenBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

        JSONObject cuerpo = new JSONObject();
        try {
            cuerpo.put("orden_id", ordenIdSeleccionada);
            cuerpo.put("imagen_base64", imagenBase64);
        } catch (JSONException e) {
            mostrarError("Error interno al armar el JSON: " + e.getMessage());
            return;
        }

        btnEnviar.setEnabled(false);
        btnFoto.setEnabled(false);
        barraEnvio.setVisibility(View.VISIBLE);
        txtResultado.setTextColor(ContextCompat.getColor(this, R.color.texto_normal));
        txtResultado.setText(getString(R.string.enviando, host()));

        JsonObjectRequest peticion = new JsonObjectRequest(Request.Method.POST,
                urlBase() + "/facturas.php", cuerpo,
                response -> {
                    JSONObject datos = response.optJSONObject("datos");
                    int idFactura = datos != null ? datos.optInt("factura_id", -1) : -1;
                    if ("OK".equals(response.optString("codigo")) && idFactura > 0) {
                        Toast.makeText(this, R.string.exito_toast, Toast.LENGTH_SHORT).show();
                        procesarFactura(idFactura);   // Sprint 4: OCR → riesgo → conciliación
                    } else {
                        finEnvio(false);
                        mostrarError(getString(R.string.respuesta_rara));
                    }
                },
                error -> {
                    finEnvio(false);
                    mostrarError("Error de envío: " + mensajeVolley(error));
                });

        // Sin reintentos: el manejo de duplicados llega en v1.0 (RF-L3)
        peticion.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1.0f));
        peticion.setTag(TAG);
        cola.add(peticion);
    }

    private void finEnvio(boolean exito) {
        barraEnvio.setVisibility(View.GONE);
        btnFoto.setEnabled(true);
        // Tras un envío exitoso hay que tomar otra foto para volver a enviar
        btnEnviar.setEnabled(!exito);
    }

    // ========================================================================
    // Sprint 4 — Procesar la factura subida (GET /api/procesar.php, RF-B3)
    // ========================================================================

    /** Abre la pantalla del listado con badges (RF-M5) con el servidor actual. */
    private void abrirListado() {
        guardarServidor();
        startActivity(new Intent(this, ListadoActivity.class)
                .putExtra("servidor", host()));
    }

    /** Dispara el pipeline OCR → riesgo → conciliación y muestra el veredicto. */
    private void procesarFactura(int idFactura) {
        txtResultado.setTextColor(ContextCompat.getColor(this, R.color.texto_normal));
        txtResultado.setText(getString(R.string.procesando, idFactura));

        JsonObjectRequest peticion = new JsonObjectRequest(Request.Method.GET,
                urlBase() + "/procesar.php?id=" + idFactura, null,
                response -> {
                    finEnvio(true);
                    JSONObject datos = response.optJSONObject("datos");
                    // «YA_PROCESADA» trae la factura anidada; «OK» la trae plana.
                    if (datos != null && !datos.has("estado") && datos.has("factura")) {
                        datos = datos.optJSONObject("factura");
                    }
                    mostrarVeredicto(idFactura, datos);
                },
                error -> {
                    finEnvio(true);
                    // RNF-5: la factura NO se perdió; quedó «Pendiente» en el servidor.
                    txtResultado.setTextColor(ContextCompat.getColor(this, R.color.ambar_alerta));
                    txtResultado.setText(getString(R.string.queda_pendiente,
                            idFactura, mensajeVolley(error)));
                });

        // El pipeline tarda ~1-3 s, pero se deja el margen completo del KPI-1
        peticion.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1.0f));
        peticion.setTag(TAG);
        cola.add(peticion);
    }

    /** Traduce la respuesta de procesar.php al mensaje final del usuario. */
    private void mostrarVeredicto(int idFactura, JSONObject datos) {
        String estado = datos != null ? datos.optString("estado", "") : "";
        String motivo = datos != null ? datos.optString("motivo_discrepancia", "") : "";
        String riesgo = datos != null ? datos.optString("riesgo", "?") : "?";
        String duracion = datos != null ? datos.optString("duracion_pipeline_s", "?") : "?";

        switch (estado) {
            case "Aprobada":
                txtResultado.setTextColor(ContextCompat.getColor(this, R.color.verde_ok));
                txtResultado.setText(getString(R.string.procesada_ok, idFactura, riesgo, duracion));
                break;
            case "Con Discrepancia":
                txtResultado.setTextColor(ContextCompat.getColor(this, R.color.ambar_alerta));
                txtResultado.setText(getString(R.string.procesada_discrepancia,
                        idFactura, motivo.isEmpty() ? "?" : motivo, riesgo, duracion));
                break;
            default:   // sigue «Pendiente» (p. ej. OCR incompleto) u otro estado
                txtResultado.setTextColor(ContextCompat.getColor(this, R.color.gris_pendiente));
                txtResultado.setText(getString(R.string.procesada_pendiente,
                        idFactura, estado.isEmpty() ? "?" : estado));
                break;
        }
    }

    // ========================================================================
    // Utilidades: imágenes y errores
    // ========================================================================

    /** Reescala a JPEG 85 con lado mayor ≤ MAX_LADO_ENVIO (límite API: 8 MB). */
    private byte[] comprimirParaEnvio() throws IOException {
        Bitmap bitmap = decodificar(archivoFoto, MAX_LADO_ENVIO);
        if (bitmap == null) {
            throw new IOException("formato de imagen no soportado");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        bitmap.recycle();
        return baos.toByteArray();
    }

    /** Decodifica con inSampleSize para no inflar la memoria. */
    private Bitmap decodificar(File archivo, int maxLado) {
        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(archivo.getAbsolutePath(), opciones);
        opciones.inSampleSize = 1;
        while (opciones.outWidth / opciones.inSampleSize > maxLado
                || opciones.outHeight / opciones.inSampleSize > maxLado) {
            opciones.inSampleSize *= 2;
        }
        opciones.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(archivo.getAbsolutePath(), opciones);
    }

    /** Extrae el "mensaje" del JSON de error producido por responder.php, si existe. */
    private String mensajeVolley(VolleyError error) {
        NetworkResponse resp = error.networkResponse;
        if (resp != null && resp.data != null) {
            try {
                JSONObject json = new JSONObject(new String(resp.data, "UTF-8"));
                String detalle = json.optString("mensaje", null);
                if (detalle != null) {
                    return "HTTP " + resp.statusCode + ": " + detalle;
                }
            } catch (Exception ignored) {
                // sin cuerpo JSON legible
            }
            return "HTTP " + resp.statusCode;
        }
        return error.getMessage() == null ? "sin detalles" : error.getMessage();
    }

    private void mostrarError(String mensaje) {
        txtResultado.setTextColor(ContextCompat.getColor(this, R.color.rojo_error));
        txtResultado.setText(mensaje);
    }

}
