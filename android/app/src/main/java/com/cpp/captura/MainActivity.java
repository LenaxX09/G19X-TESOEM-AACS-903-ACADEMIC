package com.cpp.captura;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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
import androidx.exifinterface.media.ExifInterface;

import com.android.volley.AuthFailureError;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MainActivity — Sprint 2 "Captura" del MVP CPP (pantalla de la sección 6.3).
 *
 * Flujo:
 *   1. Carga las órdenes de compra desde GET /api/ordenes.php.
 *   2. "Tomar foto": MediaStore.ACTION_IMAGE_CAPTURE + FileProvider (RF-M2).
 *      2b. "Elegir de galería": ACTION_GET_CONTENT image/* (RF-M3, adelantado
 *          de la v1.0 en la demo con móvil real: fotografiar la pantalla baja
 *          demasiado la resolución y el OCR no lee fecha/monto).
 *   3. "Enviar": comprime la foto (JPEG 85, lado mayor ≤ 1600 px), la codifica
 *      en Base64 y hace POST /api/facturas.php { orden_id, imagen_base64 }
 *      con Volley (RF-M4, versión simplificada solo Base64).
 *   4. Sprint 4 (RF-B3): al recibir el id, llama a GET /api/procesar.php?id=N
 *      (OCR → riesgo → conciliación en el servidor) y muestra el veredicto:
 *      ✅ Aprobada / ⚠️ Con Discrepancia, con % de riesgo y duración.
 *   5. Botón "Ver facturas" → ListadoActivity con badges (RF-M5).
 *   6. Sprint 5 (RF-M1/RF-B6): TODA petición viaja firmada con el token de
 *      sesión (cabecera X-CPP-Token, clase Sesion); un 401 regresa al login.
 *      «⚙️ Administración» (visible solo para admin) abre el CRUD y «🚪 Cerrar
 *      sesión» hace logout. El rol revisor solo consulta: no puede enviar.
 *
 * Servidor: Laragon en el PC de desarrollo.
 *   - Emulador:     usar 10.0.2.2 (alias del host local).
 *   - Móvil físico: usar la IP LAN del PC (misma red WiFi).
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CPP_S2";
    private static final int REQ_CAMARA = 1001;
    private static final int REQ_PERMISO_CAMARA = 1002;
    private static final int REQ_GALERIA = 1003;      // RF-M3: ACTION_GET_CONTENT
    private static final int MAX_LADO_ENVIO = 1600;    // px del lado mayor al enviar
    private static final int MAX_LADO_PREVIEW = 1000;  // px del lado mayor en pantalla
    private static final int TIMEOUT_MS = 30000;       // KPI-1: pipeline ≤ 30 s

    private RequestQueue cola;

    private EditText txtServidor;
    private Spinner spOrdenes;
    private ImageView imgPreview;
    private Button btnCargar;
    private Button btnFoto;
    private Button btnGaleria;
    private Button btnEnviar;
    private Button btnLista;
    private Button btnAdmin;       // Sprint 5: CRUD de usuarios/órdenes (solo admin)
    private Button btnSalir;       // Sprint 5: cerrar sesión (RF-M1)
    private TextView txtSesion;    // Sprint 5: «👤 nombre (rol) · servidor»
    private ProgressBar barraEnvio;
    private TextView txtResultado;
    private TextView txtPreviewHint;  // 0.6.0-ui: marcador «sin foto» sobre la vista previa

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

        // 0.7.1-ui: sin ActionBar «Cuentas» — el banner ya identifica la app
        // y se gana todo ese espacio vertical para el contenido.
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        cola = Volley.newRequestQueue(this);

        txtServidor = findViewById(R.id.txtServidor);
        spOrdenes = findViewById(R.id.spOrdenes);
        imgPreview = findViewById(R.id.imgPreview);
        btnCargar = findViewById(R.id.btnCargar);
        btnFoto = findViewById(R.id.btnFoto);
        btnGaleria = findViewById(R.id.btnGaleria);
        btnEnviar = findViewById(R.id.btnEnviar);
        btnLista = findViewById(R.id.btnLista);
        btnAdmin = findViewById(R.id.btnAdmin);
        btnSalir = findViewById(R.id.btnSalir);
        txtSesion = findViewById(R.id.txtSesion);
        barraEnvio = findViewById(R.id.barraEnvio);
        txtResultado = findViewById(R.id.txtResultado);
        txtPreviewHint = findViewById(R.id.txtPreviewHint);

        // Sprint 5: la IP y la sesión (token/rol/nombre) viven en la clase Sesion;
        // aquí solo se muestra quién está conectado y a qué servidor.
        txtServidor.setText(Sesion.servidor(this));
        txtSesion.setText(getString(R.string.sesion_info,
                Sesion.nombre(this), Sesion.rol(this), Sesion.servidor(this)));
        if (!Sesion.esAdmin(this)) {
            btnAdmin.setVisibility(View.GONE);   // RF-B6: el CRUD es solo para admin
        }

        // Sprint 8 (RF-P1): cuentas por pagar — apartado del CLIENTE
        // (capturista/admin/revisor); al proveedor se le oculta.
        Button btnPagos = findViewById(R.id.btnPagos);
        if (Sesion.esProveedor(this)) {
            btnPagos.setVisibility(View.GONE);
        } else {
            btnPagos.setOnClickListener(v ->
                    startActivity(new Intent(this, PagosActivity.class)));
        }

        btnCargar.setOnClickListener(v -> {
            guardarServidor();
            cargarOrdenes();
        });
        btnFoto.setOnClickListener(v -> pedirCamara());
        btnGaleria.setOnClickListener(v -> elegirDeGaleria());
        btnEnviar.setOnClickListener(v -> enviarFactura());
        btnLista.setOnClickListener(v -> abrirListado());
        btnAdmin.setOnClickListener(v ->
                startActivity(new Intent(this, AdminActivity.class)));
        btnSalir.setOnClickListener(v -> cerrarSesion());

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
        Sesion.setServidor(this, host());   // Sprint 5: la IP se comparte con el login
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
        pintarResultado(R.color.texto_normal,
                "Consultando órdenes en " + urlBase() + "/ordenes.php …");

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
                                R.layout.fila_spinner, etiquetas);   // 0.8.1-night: color fijo (tarjeta clara)
                        adaptador.setDropDownViewResource(
                                android.R.layout.simple_spinner_dropdown_item);
                        spOrdenes.setAdapter(adaptador);
                        pintarResultado(R.color.texto_normal,
                                "Órdenes cargadas: " + ordenes.size()
                                        + ". Ya puedes tomar la foto.");
                    } catch (JSONException e) {
                        mostrarError("Respuesta inesperada del servidor: " + e.getMessage());
                    }
                },
                error -> {
                    if (Sesion.sinSesion(error)) {
                        sesionCaducada();
                        return;
                    }
                    mostrarError("No se pudo conectar con " + host()
                            + ". ¿Está Laragon iniciado? (" + mensajeVolley(error) + ")");
                }) {
            // Sprint 5: toda petición sale firmada con el token (RF-M1/RF-B6)
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>(super.getHeaders());
                h.putAll(Sesion.cabeceras(MainActivity.this));
                return h;
            }
        };
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

    // ========================================================================
    // Galería — RF-M3 (adelantado de la v1.0): ACTION_GET_CONTENT image/*
    // ========================================================================

    /**
     * Abre el selector de imágenes del sistema. No requiere permiso de
     * almacenamiento: el selector devuelve un content:// con lectura temporal.
     * Motivo del adelanto: fotografiar una factura EN PANTALLA reduce demasiado
     * la resolución y el OCR no logra leer fecha/monto; con la imagen original
     * de la galería el OCR recibe los 1600 px completos.
     */
    private void elegirDeGaleria() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQ_GALERIA);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.sin_app_galeria, Toast.LENGTH_LONG).show();
        }
    }

    /** Copia la imagen elegida (content://) al archivo de caché de la app. */
    private boolean copiarDeGaleria(Uri uri) {
        File destino = new File(getCacheDir(), "captura_factura.jpg");
        try (InputStream entrada = getContentResolver().openInputStream(uri);
             FileOutputStream salida = new FileOutputStream(destino)) {
            byte[] buffer = new byte[8192];
            int leidos;
            while ((leidos = entrada.read(buffer)) > 0) {
                salida.write(buffer, 0, leidos);
            }
            archivoFoto = destino;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_GALERIA) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                pintarResultado(R.color.gris_pendiente, getString(R.string.foto_cancelada));
                return;
            }
            if (!copiarDeGaleria(data.getData())) {
                mostrarError(getString(R.string.galeria_error));
                return;
            }
            mostrarFotoLista();
            return;
        }
        if (requestCode != REQ_CAMARA) {
            return;
        }
        if (resultCode != RESULT_OK || archivoFoto == null
                || !archivoFoto.exists() || archivoFoto.length() == 0) {
            pintarResultado(R.color.gris_pendiente, getString(R.string.foto_cancelada));
            return;
        }
        mostrarFotoLista();
    }

    /** Muestra la vista previa y habilita «Enviar» (común a cámara y galería). */
    private void mostrarFotoLista() {
        Bitmap preview = decodificar(archivoFoto, MAX_LADO_PREVIEW);
        if (preview == null) {
            mostrarError("No se pudo leer la imagen (¿formato no soportado?).");
            return;
        }
        imgPreview.setImageBitmap(preview);
        txtPreviewHint.setVisibility(View.GONE);
        if (Sesion.esRevisor(this)) {
            // RF-B6 en la app: el revisor es de CONSULTA; la API además lo
            // rechazaría con 403, pero es mejor explicarlo aquí.
            btnEnviar.setEnabled(false);
            pintarResultado(R.color.ambar_alerta, getString(R.string.revisor_solo_lectura));
            return;
        }
        btnEnviar.setEnabled(true);
        pintarResultado(R.color.texto_normal, getString(R.string.foto_lista));
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
        btnGaleria.setEnabled(false);
        barraEnvio.setVisibility(View.VISIBLE);
        pintarResultado(R.color.texto_normal, getString(R.string.enviando, host()));

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
                    if (Sesion.sinSesion(error)) {
                        sesionCaducada();
                        return;
                    }
                    mostrarError("Error de envío: " + mensajeVolley(error));
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>(super.getHeaders());
                h.putAll(Sesion.cabeceras(MainActivity.this));
                return h;
            }
        };

        // Sin reintentos: el manejo de duplicados llega en v1.0 (RF-L3)
        peticion.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1.0f));
        peticion.setTag(TAG);
        cola.add(peticion);
    }

    private void finEnvio(boolean exito) {
        barraEnvio.setVisibility(View.GONE);
        btnFoto.setEnabled(true);
        btnGaleria.setEnabled(true);
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

    // ========================================================================
    // Sprint 5 — sesión (RF-M1 / RF-B6)
    // ========================================================================

    /** El servidor respondió 401: la sesión expiró o se cerró → al login. */
    private void sesionCaducada() {
        Toast.makeText(this, R.string.sesion_caducada, Toast.LENGTH_LONG).show();
        Sesion.salirAlLogin(this);
    }

    /**
     * Logout: DELETE /api/sesion.php con el token (borra la fila en `sesiones`
     * en el servidor) y, llegue respuesta o error, se limpia la sesión local
     * y se regresa al login. Fire-and-forget a propósito.
     */
    private void cerrarSesion() {
        JsonObjectRequest peticion = new JsonObjectRequest(Request.Method.DELETE,
                urlBase() + "/sesion.php", null,
                response -> Sesion.salirAlLogin(MainActivity.this),
                error -> Sesion.salirAlLogin(MainActivity.this)) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>(super.getHeaders());
                h.putAll(Sesion.cabeceras(MainActivity.this));
                return h;
            }
        };
        peticion.setTag(TAG);
        cola.add(peticion);
    }

    /** Dispara el pipeline OCR → riesgo → conciliación y muestra el veredicto. */
    private void procesarFactura(int idFactura) {
        pintarResultado(R.color.texto_normal, getString(R.string.procesando, idFactura));

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
                    if (Sesion.sinSesion(error)) {
                        sesionCaducada();
                        return;
                    }
                    // RNF-5: la factura NO se perdió; quedó «Pendiente» en el servidor.
                    pintarResultado(R.color.ambar_alerta, getString(R.string.queda_pendiente,
                            idFactura, mensajeVolley(error)));
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>(super.getHeaders());
                h.putAll(Sesion.cabeceras(MainActivity.this));
                return h;
            }
        };

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
                pintarResultado(R.color.verde_ok,
                        getString(R.string.procesada_ok, idFactura, riesgo, duracion));
                break;
            case "Con Discrepancia":
                pintarResultado(R.color.ambar_alerta, getString(R.string.procesada_discrepancia,
                        idFactura, motivo.isEmpty() ? "?" : motivo, riesgo, duracion));
                break;
            default:   // sigue «Pendiente» (p. ej. OCR incompleto) u otro estado
                pintarResultado(R.color.gris_pendiente, getString(R.string.procesada_pendiente,
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

    /**
     * Decodifica con inSampleSize para no inflar la memoria y reescala EXACTO
     * al lado mayor pedido.
     * HOTFIX 0.4.2: inSampleSize solo reduce en POTENCIAS DE 2, de modo que una
     * foto de cámara de p.ej. 4128x1856 quedaba en 1024x461 (perdía ~36% de la
     * resolución frente a los 1600 px objetivo) y el OCR no lograba leerla.
     * Ahora: muestreo hasta <= 2*maxLado y createScaledBitmap al tamaño exacto.
     */
    private Bitmap decodificar(File archivo, int maxLado) {
        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(archivo.getAbsolutePath(), opciones);
        opciones.inSampleSize = 1;
        while (opciones.outWidth / opciones.inSampleSize > maxLado * 2
                || opciones.outHeight / opciones.inSampleSize > maxLado * 2) {
            opciones.inSampleSize *= 2;
        }
        opciones.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(archivo.getAbsolutePath(), opciones);
        if (bitmap == null) {
            return null;
        }
        bitmap = orientarSegunExif(archivo, bitmap);
        int ladoMayor = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (ladoMayor > maxLado) {
            float escala = (float) maxLado / ladoMayor;
            Bitmap escalado = Bitmap.createScaledBitmap(bitmap,
                    Math.round(bitmap.getWidth() * escala),
                    Math.round(bitmap.getHeight() * escala), true);
            if (escalado != bitmap) {
                bitmap.recycle();
            }
            return escalado;
        }
        return bitmap;
    }

    /**
     * HOTFIX 0.4.3: muchas cámaras (p.ej. Motorola) guardan el pixel-array en
     * landscape y anotan la rotación en el tag EXIF Orientation. BitmapFactory
     * IGNORA ese tag, así que la foto tomada en vertical llegaba "acostada" a
     * la vista previa y al servidor (el OCR no puede leer texto girado 90°).
     * Aquí se rota el bitmap según el EXIF ANTES de reescalar; el JPEG final
     * viaja ya vertical y sin depender de metadatos.
     */
    private Bitmap orientarSegunExif(File archivo, Bitmap bitmap) {
        try {
            ExifInterface exif = new ExifInterface(archivo.getAbsolutePath());
            int orientacion = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int grados;
            switch (orientacion) {
                case ExifInterface.ORIENTATION_ROTATE_90:  grados = 90;  break;
                case ExifInterface.ORIENTATION_ROTATE_180: grados = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: grados = 270; break;
                default: return bitmap; // normal/desconocida: sin rotación
            }
            Matrix matriz = new Matrix();
            matriz.postRotate(grados);
            Bitmap rotado = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matriz, true);
            if (rotado != bitmap) {
                bitmap.recycle();
            }
            return rotado;
        } catch (Exception e) {
            return bitmap; // sin EXIF legible: la foto se devuelve tal cual
        }
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
        pintarResultado(R.color.rojo_error, mensaje);
    }

    /**
     * 0.6.0-ui: el resultado se pinta como PANEL de veredicto — texto en el color
     * del estado sobre un fondo pastel del mismo tono (el tinte, al ~20% de alfa,
     * se aplica al rectángulo redondeado blanco del layout). Así ✅/⚠️/❌ se
     * distinguen de un vistazo sin leer el texto (hipótesis H4 del MVP).
     */
    private void pintarResultado(int colorRes, CharSequence mensaje) {
        int color = ContextCompat.getColor(this, colorRes);
        txtResultado.setVisibility(View.VISIBLE);
        txtResultado.setTextColor(color);
        txtResultado.setBackgroundTintList(ColorStateList.valueOf(
                (color & 0x00FFFFFF) | 0x33000000));
        txtResultado.setText(mensaje);
    }

}
