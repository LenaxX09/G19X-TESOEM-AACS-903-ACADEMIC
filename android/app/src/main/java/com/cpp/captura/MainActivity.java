package com.cpp.captura;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MainActivity — Sprint 10 «Sin servidor»: el pipeline corre EN EL TELÉFONO.
 *
 * Flujo (equivalente al de Laragon, sin Laragon):
 *   1. El spinner se llena con las CUENTAS que el PROVEEDOR subió a Firestore
 *      para el correo de este cliente (oyente en vivo, sin IP que teclear).
 *   2. Foto (RF-M2) o galería (RF-M3) → EXIF + ≤1600 px + JPEG 85.
 *   3. «Enviar factura» ejecuta el pipeline local con la clase Motores:
 *      ML Kit lee el texto (OCR), clasificadorRiesgo() calcula el % (fórmula
 *      del C), buscarDuplicado() hace el pre-check RF-L3 en Firestore y
 *      conciliar() aplica las reglas del Prolog → veredicto ✅/⚠️.
 *   4. La foto va a Firebase Storage y el resultado a Firestore `facturas`
 *      (el listado y los pagos se alimentan de ahí). Si Storage no está
 *      disponible (omisión Blaze), la factura se guarda SIN foto y con
 *      aviso — el veredicto nunca se pierde (correcciones 31/10).
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_CAMARA = 1001;
    private static final int REQ_PERMISO_CAMARA = 1002;
    private static final int REQ_GALERIA = 1003;
    private static final int REQ_NOTIFICACIONES = 1004;   // S10.4: recordatorios
    private static final int MAX_LADO_ENVIO = 1600;
    private static final int MAX_LADO_PREVIEW = 1000;

    private Spinner spOrdenes;
    private ImageView imgPreview;
    private Button btnFoto;
    private Button btnGaleria;
    private Button btnEnviar;
    private TextView txtSesion;
    private ProgressBar barraEnvio;
    private TextView txtResultado;
    private TextView txtPreviewHint;

    private File archivoFoto;
    private final List<Orden> ordenes = new ArrayList<>();
    private int ordenPos = -1;

    /** Cuenta subida por el proveedor (documento de Firestore `ordenes`). */
    private static class Orden {
        String id;
        String codigo;
        String negocio;
        double monto;
        String fechaEmision;
        String fechaLimite;

        String etiqueta() {
            return codigo + " · " + negocio + "  ($" + String.format(Locale.US, "%.2f", monto) + ")"
                    + (fechaLimite == null || fechaLimite.isEmpty() ? "" : " · vence " + fechaLimite);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // S10.5: candado obligatorio — sin verificar huella/contraseña no se
        // entra (cubre también llegar aquí tocando una notificación).
        if (Candado.requiereCandado(this)) {
            startActivity(new Intent(this, CandadoActivity.class));
            finish();
            return;
        }

        // S10.7: vigilancia de sesión — si BORRAN esta cuenta en Firebase,
        // el teléfono se entera EN VIVO y regresa al login.
        FirebaseServicio.vigilarSesion(this);

        setContentView(R.layout.activity_main);

        txtSesion = findViewById(R.id.txtSesion);
        spOrdenes = findViewById(R.id.spOrdenes);
        imgPreview = findViewById(R.id.imgPreview);
        txtPreviewHint = findViewById(R.id.txtPreviewHint);
        btnFoto = findViewById(R.id.btnFoto);
        btnGaleria = findViewById(R.id.btnGaleria);
        btnEnviar = findViewById(R.id.btnEnviar);
        barraEnvio = findViewById(R.id.barraEnvio);
        txtResultado = findViewById(R.id.txtResultado);

        // S10.3: la línea de sesión muestra el PERFIL DE LA EMPRESA (rol
        // único «cliente»: su empresa) — no hace falta el viejo rol interno.
        String empresa = Sesion.negocio(this);
        txtSesion.setText(getString(R.string.sesion_linea,
                Sesion.nombre(this),
                empresa == null || empresa.isEmpty() ? getString(R.string.rol_cliente) : empresa,
                Sesion.correo(this)));

        // S10.3: sin CRUD de usuarios — cada empresa cliente es UN correo
        // y nadie crea más usuarios; «Administración» desapareció con los roles.
        findViewById(R.id.btnAdmin).setVisibility(View.GONE);

        // S10.4: recordatorios de pago — el teléfono revisa SOLO las cuentas
        // de la empresa (una vez al día + un chequeo al abrir la app) y avisa
        // cuando algo vence en 3 días, mañana o ya venció.
        RecordatorioWorker.programar(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICACIONES);
        }

        btnFoto.setOnClickListener(v -> pedirCamara());
        btnGaleria.setOnClickListener(v -> elegirDeGaleria());
        btnEnviar.setOnClickListener(v -> enviarFactura());
        findViewById(R.id.btnLista).setOnClickListener(v ->
                startActivity(new Intent(this, ListadoActivity.class)));
        findViewById(R.id.btnPagos).setOnClickListener(v ->
                startActivity(new Intent(this, PagosActivity.class)));
        findViewById(R.id.btnSalir).setOnClickListener(v -> {
            Toast.makeText(this, R.string.sesion_cerrada, Toast.LENGTH_SHORT).show();
            Sesion.salirAlLogin(this);
        });

        spOrdenes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                ordenPos = pos;
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
                ordenPos = -1;
            }
        });

        cargarOrdenes();
    }

    /**
     * S10.5 (fix 22/10): al VOLVER del segundo plano con la sesión
     * abierta, App ya re-bloqueó el candado — esta pantalla no se
     * muestra hasta verificar la huella/PIN otra vez.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (Candado.requiereCandado(this)) {
            startActivity(new Intent(this, CandadoActivity.class));
            finish();
        }
    }

    // ========================================================================
    // Cuentas del cliente (Firestore, en vivo) — las sube el PROVEEDOR
    // ========================================================================

    private void cargarOrdenes() {
        FirebaseServicio.escucharOrdenesCliente(Sesion.correo(this), lista ->
                runOnUiThread(() -> {
                    ordenes.clear();
                    for (QueryDocumentSnapshot d : lista) {
                        Orden o = new Orden();
                        o.id = d.getId();
                        o.codigo = nulo(d.getString("codigo"));
                        o.negocio = nulo(d.getString("negocio"));
                        Double monto = d.getDouble("monto");
                        o.monto = monto == null ? 0 : monto;
                        o.fechaEmision = nulo(d.getString("fechaEmision"));
                        o.fechaLimite = nulo(d.getString("fechaLimite"));
                        ordenes.add(o);
                    }
                    Collections.sort(ordenes, (a, b) -> a.codigo.compareTo(b.codigo));

                    List<String> etiquetas = new ArrayList<>();
                    for (Orden o : ordenes) {
                        etiquetas.add(o.etiqueta());
                    }
                    ArrayAdapter<String> adaptador = new ArrayAdapter<>(this,
                            R.layout.fila_spinner, etiquetas);
                    adaptador.setDropDownViewResource(
                            android.R.layout.simple_spinner_dropdown_item);
                    spOrdenes.setAdapter(adaptador);

                    if (ordenes.isEmpty()) {
                        pintarResultado(R.color.ambar_alerta,
                                getString(R.string.ordenes_sin_datos));
                    } else {
                        pintarResultado(R.color.texto_normal,
                                "Cuentas cargadas: " + ordenes.size()
                                        + ". Ya puedes tomar la foto.");
                    }
                }));
    }

    private String nulo(String s) {
        return s == null ? "" : s;
    }

    // ========================================================================
    // Cámara (RF-M2) y galería (RF-M3) — igual que siempre
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

        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQ_CAMARA);
        } else {
            Toast.makeText(this, R.string.sin_app_camara, Toast.LENGTH_LONG).show();
        }
    }

    private void elegirDeGaleria() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQ_GALERIA);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.sin_app_galeria, Toast.LENGTH_LONG).show();
        }
    }

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

    private void mostrarFotoLista() {
        Bitmap preview = decodificar(archivoFoto, MAX_LADO_PREVIEW);
        if (preview == null) {
            mostrarError("No se pudo leer la imagen (¿formato no soportado?).");
            return;
        }
        imgPreview.setImageBitmap(preview);
        txtPreviewHint.setVisibility(View.GONE);
        btnEnviar.setEnabled(true);
        pintarResultado(R.color.texto_normal, getString(R.string.foto_lista));
    }

    // ========================================================================
    // Enviar → pipeline EN EL TELÉFONO: ML Kit → riesgo → duplicados → reglas
    // ========================================================================

    private void enviarFactura() {
        if (ordenPos < 0 || ordenPos >= ordenes.size()) {
            mostrarError(getString(R.string.falta_orden));
            return;
        }
        if (archivoFoto == null || !archivoFoto.exists()) {
            mostrarError(getString(R.string.falta_foto));
            return;
        }

        Bitmap bitmap = decodificar(archivoFoto, MAX_LADO_ENVIO);
        if (bitmap == null) {
            mostrarError("No se pudo leer la imagen (¿formato no soportado?).");
            return;
        }
        byte[] bytes = comprimir(bitmap);

        btnFoto.setEnabled(false);
        btnGaleria.setEnabled(false);
        btnEnviar.setEnabled(false);
        barraEnvio.setVisibility(View.VISIBLE);
        final long t0 = System.currentTimeMillis();
        pintarResultado(R.color.texto_normal, getString(R.string.ocr_movil));

        Motores.ocr(bitmap,
                texto -> runOnUiThread(() -> correrPipeline(texto, bytes, t0)),
                e -> runOnUiThread(() -> {
                    finEnvio(false);
                    mostrarError(e);
                }));
    }

    private void correrPipeline(String texto, byte[] bytes, long t0) {
        Motores.Campos c = Motores.extraerCampos(texto);
        List<String> faltan = new ArrayList<>();
        if (c.proveedor == null || c.proveedor.length() < 4) {
            faltan.add("proveedor");
        }
        if (c.fecha == null || !c.fecha.matches("\\d{4}-\\d{2}-\\d{2}")) {
            faltan.add("fecha");
        }
        if (c.monto == null || c.monto <= 0) {
            faltan.add("monto");
        }
        if (!faltan.isEmpty()) {
            finEnvio(false);
            pintarResultado(R.color.ambar_alerta,
                    getString(R.string.ocr_incompleto, String.join(", ", faltan)));
            return;
        }

        final Orden orden = ordenes.get(ordenPos);
        final String slug = FirebaseServicio.slug(orden.negocio);

        FirebaseServicio.historialPromedio(Sesion.correo(this), slug, promedio -> runOnUiThread(() -> {
            final double riesgo = Motores.clasificadorRiesgo(c.monto, promedio);
            FirebaseServicio.buscarDuplicado(Sesion.correo(this), c.folio, c.monto,
                    c.fecha, slug,
                    originalId -> runOnUiThread(() ->
                            conciliarYGuardar(orden, c, riesgo, originalId != null, bytes, t0)),
                    e -> runOnUiThread(() -> {
                        finEnvio(false);
                        mostrarError("No se pudo revisar duplicados: " + e);
                    }));
        }));
    }

    private void conciliarYGuardar(Orden orden, Motores.Campos c, double riesgo,
                                   boolean duplicado, byte[] bytes, long t0) {
        Motores.Orden mo = new Motores.Orden();
        mo.codigo = orden.codigo;
        mo.negocio = orden.negocio;
        mo.monto = orden.monto;
        mo.fechaEmision = orden.fechaEmision;

        String veredicto = Motores.conciliar(mo, c, riesgo, duplicado);

        Map<String, Object> factura = new HashMap<>();
        factura.put("folio", c.folio == null ? "" : c.folio);
        factura.put("proveedor", c.proveedor);
        factura.put("proveedorSlug", FirebaseServicio.slug(c.proveedor));
        factura.put("fecha", c.fecha);
        factura.put("monto", c.monto);
        factura.put("riesgo", riesgo);
        factura.put("veredicto", veredicto);
        factura.put("motivo", veredicto.startsWith("DISCREPANCIA:")
                ? veredicto.substring(veredicto.indexOf(':') + 1) : "");
        factura.put("ordenId", orden.id);
        factura.put("ordenCodigo", orden.codigo);
        factura.put("subidoPor", Sesion.correo(this));
        factura.put("creado", FieldValue.serverTimestamp());

        // Omisión de Storage (31/10 — sin Blaze no hay bucket; INSTALACION §8.4):
        // si la foto NO puede subir, la factura se GUARDA IGUAL con fotoUrl
        // vacía — el veredicto, el listado y los pagos NO dependen de Storage.
        // Solo se pierde la imagen y se avisa (el pago con comprobante sigue
        // EXIGIENDO su foto: sin evidencia no hay cuenta pagada).
        FirebaseServicio.subirFotoFactura(bytes,
                url -> runOnUiThread(() -> {
                    factura.put("fotoUrl", url);
                    guardarFacturaConAviso(factura, veredicto, riesgo, t0, null);
                }),
                e -> runOnUiThread(() -> {
                    factura.put("fotoUrl", "");
                    guardarFacturaConAviso(factura, veredicto, riesgo, t0,
                            "Foto sin subir (Storage omitido por Blaze): "
                                    + "factura guardada SIN imagen.");
                }));
    }

    /** Guarda la factura en Firestore y muestra el veredicto. avisoFoto != null
     *  significa que la foto no subió (Storage omitido): la factura se guardó
     *  igual y se le avisa al usuario (correcciones 31/10). */
    private void guardarFacturaConAviso(Map<String, Object> factura, String veredicto,
                                        double riesgo, long t0, String avisoFoto) {
        FirebaseServicio.guardarFactura(factura,
                id -> runOnUiThread(() -> {
                    finEnvio(true);
                    mostrarVeredicto(veredicto, riesgo, t0);
                    if (avisoFoto != null) {
                        Toast.makeText(this, avisoFoto, Toast.LENGTH_LONG).show();
                    }
                }),
                e -> runOnUiThread(() -> {
                    finEnvio(false);
                    mostrarError("No se pudo guardar la factura: " + e);
                }));
    }

    private void mostrarVeredicto(String veredicto, double riesgo, long t0) {
        double segundos = (System.currentTimeMillis() - t0) / 1000.0;
        String duracion = String.format(Locale.US, "%.1f", segundos);
        String riesgoTxt = String.format(Locale.US, "%.1f", riesgo);

        if ("APROBADA".equals(veredicto)) {
            pintarResultado(R.color.verde_ok,
                    "Factura APROBADA · riesgo " + riesgoTxt + "% · " + duracion
                            + " s\nPipeline corrido EN EL TELÉFONO (ML Kit + Java). "
                            + "También quedó en el listado.");
        } else {
            String motivo = veredicto.substring(veredicto.indexOf(':') + 1);
            pintarResultado(R.color.ambar_alerta,
                    "Factura CON DISCREPANCIA\nMotivo: " + motivo
                            + "\nriesgo " + riesgoTxt + "% · " + duracion + " s");
        }
    }

    private void finEnvio(boolean exito) {
        barraEnvio.setVisibility(View.GONE);
        btnFoto.setEnabled(true);
        btnGaleria.setEnabled(true);
        btnEnviar.setEnabled(!exito);   // tras éxito: nueva foto para reenviar
    }

    // ========================================================================
    // Utilidades: imágenes (mismos hotfixes 0.4.2/0.4.3) y panel de resultado
    // ========================================================================

    private byte[] comprimir(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        return baos.toByteArray();
    }

    /**
     * Decodifica con inSampleSize y reescala EXACTO (hotfix 0.4.2), rotando
     * antes según EXIF (hotfix 0.4.3: las fotos verticales llegaban acostadas).
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
                default: return bitmap;
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
            return bitmap;
        }
    }

    private void mostrarError(String mensaje) {
        pintarResultado(R.color.rojo_error, mensaje);
    }

    /** Panel pastel de veredicto (tinte sobre bg_veredicto, 0.6.0-ui). */
    private void pintarResultado(int colorRes, CharSequence mensaje) {
        int color = getColor(colorRes);
        txtResultado.setVisibility(View.VISIBLE);
        txtResultado.setTextColor(color);
        txtResultado.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                (color & 0x00FFFFFF) | 0x33000000));
        txtResultado.setText(mensaje);
    }
}
