package com.cpp.captura;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * FirebaseServicio — Sprint 6 "Firebase" (RF-F1..F4).
 *
 * Firebase NO reemplaza al pipeline: MySQL/PHP siguen siendo el cerebro.
 * Aquí vive solo la capa de "radar y avisos":
 *
 *   init()                → auth anónima (las reglas de Firestore exigen
 *                           "app legítima"; no pedimos cuentas Google).
 *   registrarCliente()    → cada usuario que inicia sesión queda en la
 *                           colección `clientes` (RF-F1/F2).
 *   registrarProveedor()  → el teléfono queda asociado al proveedor en
 *                           `proveedores` y suscrito al tema FCM
 *                           proveedor_<slug> (RF-F3).
 *   escuchar()            → oyente EN TIEMPO REAL de `notificaciones`
 *                           (app abierta; con la app cerrada llega el
 *                           push que envía el PHP — PushServicio).
 *
 * El slug normaliza el nombre del proveedor IGUAL que fb_slug() de PHP
 * (sin acentos, minúsculas, solo letras/números): "Ferretería El Tornillo"
 * → "ferreteriaeltornillo". Con ese slug coinciden tema FCM + documento.
 *
 * Todo es BEST-EFFORT: si Firebase falla, la app sigue funcionando igual
 * (solo quedará el aviso en el log).
 */
public final class FirebaseServicio {

    private static final String TAG = "CPP_S6_FB";

    private FirebaseServicio() {
        // clase utilitaria estática
    }

    /** Auth anónima: habilitada en la consola (Authentication → Anonymous). */
    public static void init() {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                auth.signInAnonymously()
                        .addOnFailureListener(e ->
                                Log.w(TAG, "auth anónima falló: " + e.getMessage()));
            }
        } catch (Exception e) {
            Log.w(TAG, "init: " + e.getMessage());
        }
    }

    /** RF-F2: cada usuario que entra a la app queda registrado en Firestore. */
    public static void registrarCliente(int id, String nombre, String usuario, String rol) {
        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("usuario_id", id);
            doc.put("nombre", nombre);
            doc.put("usuario", usuario);
            doc.put("rol", rol);
            doc.put("ultimo_login", FieldValue.serverTimestamp());
            FirebaseFirestore.getInstance().collection("clientes")
                    .document(usuario)          // un doc por usuario; reentrar actualiza
                    .set(doc)
                    .addOnFailureListener(e ->
                            Log.w(TAG, "clientes: " + e.getMessage()));
        } catch (Exception e) {
            Log.w(TAG, "registrarCliente: " + e.getMessage());
        }
    }

    /** Normaliza el nombre del proveedor → slug (espejo de fb_slug en PHP). */
    public static String slug(String proveedor) {
        String s = Normalizer.normalize(proveedor == null ? "" : proveedor,
                Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    /** RF-F3: asocia ESTE teléfono al proveedor (doc + tema FCM). */
    public static void registrarProveedor(Context c, String proveedor) {
        final String s = slug(proveedor);
        try {
            FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(token -> {
                        Map<String, Object> doc = new HashMap<>();
                        doc.put("nombre", proveedor);
                        doc.put("slug", s);
                        doc.put("token", token);
                        doc.put("actualizado", FieldValue.serverTimestamp());
                        FirebaseFirestore.getInstance().collection("proveedores")
                                .document(s).set(doc)
                                .addOnFailureListener(e ->
                                        Log.w(TAG, "proveedores: " + e.getMessage()));
                    });
            FirebaseMessaging.getInstance().subscribeToTopic("proveedor_" + s);
            c.getSharedPreferences("cpp_sesion", Context.MODE_PRIVATE).edit()
                    .putString("proveedor", proveedor).apply();
        } catch (Exception e) {
            Log.w(TAG, "registrarProveedor: " + e.getMessage());
        }
    }

    /** Proveedor asociado a este teléfono (vacío si nunca se registró). */
    public static String proveedorGuardado(Context c) {
        return c.getSharedPreferences("cpp_sesion", Context.MODE_PRIVATE)
                .getString("proveedor", "");
    }

    /** Notificación tal como la escribe el PHP en Firestore. */
    public static class Noti {
        public final String titulo;
        public final String detalle;
        public final String fecha;

        Noti(String titulo, String detalle, String fecha) {
            this.titulo = titulo;
            this.detalle = detalle;
            this.fecha = fecha;
        }
    }

    /**
     * RF-F4: oyente en TIEMPO REAL de las notificaciones del proveedor.
     * Sin orderBy en la consulta (evita pedir índice compuesto en la
     * consola): se ordena en Java por fecha descendente.
     */
    public static void escuchar(String proveedor, Consumer<List<Noti>> alLlegar) {
        try {
            FirebaseFirestore.getInstance().collection("notificaciones")
                    .whereEqualTo("slug", slug(proveedor))
                    .limit(25)
                    .addSnapshotListener((valor, error) -> {
                        if (error != null || valor == null) {
                            Log.w(TAG, "listener: " + (error != null ? error.getMessage() : "sin datos"));
                            return;
                        }
                        List<Noti> lista = new ArrayList<>();
                        for (QueryDocumentSnapshot d : valor) {
                            Long fid = d.getLong("factura_id");
                            String orden = d.getString("orden");
                            String monto = d.getString("monto");
                            Date creado = d.getDate("creado");
                            lista.add(new Noti(
                                    "🧾 Factura #" + (fid == null ? "?" : fid)
                                            + (orden == null ? "" : " · " + orden),
                                    "Registrada para conciliación"
                                            + (monto != null && !monto.isEmpty() ? " · " + monto : ""),
                                    creado == null ? "" : new SimpleDateFormat(
                                            "dd/MM/yy HH:mm", Locale.getDefault()).format(creado)));
                        }
                        lista.sort((a, b) -> b.fecha.compareTo(a.fecha));
                        alLlegar.accept(lista);
                    });
        } catch (Exception e) {
            Log.w(TAG, "escuchar: " + e.getMessage());
        }
    }
}
