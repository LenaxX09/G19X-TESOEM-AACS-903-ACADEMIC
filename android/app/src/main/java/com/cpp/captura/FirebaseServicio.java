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
                                    "Factura #" + (fid == null ? "?" : fid)
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

    // ========================================================================
    // Sprint 10 «Sin servidor»: Firestore + Storage como backend COMPLETO
    // (Auth da la identidad; aquí viven perfiles, cuentas/órdenes, facturas
    //  conciliadas en el teléfono y los comprobantes de pago).
    // ========================================================================

    /** Callback de perfil: valores o mensaje de error. */
    public interface PerfilCb {
        void listo(String nombre, String correo, String rol, String negocio);
        void error(String mensaje);
    }

    /** Lee usuarios/{uid} (el rol y el negocio viven en el perfil). */
    public static void cargarPerfil(String uid, PerfilCb cb) {
        FirebaseFirestore.getInstance().collection("usuarios").document(uid).get()
                .addOnSuccessListener(d -> {
                    if (d.exists()) {
                        cb.listo(d.getString("nombre"), d.getString("correo"),
                                d.getString("rol"), d.getString("negocio"));
                    } else {
                        cb.error("Tu cuenta no tiene perfil en Firestore.");
                    }
                })
                .addOnFailureListener(e -> cb.error(e.getMessage()));
    }

    /** Registro público de proveedor: crea su perfil con rol "proveedor". */
    public static void crearPerfilProveedor(String uid, String nombre, String correo,
                                            String negocio, Runnable ok, Consumer<String> err) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("nombre", nombre);
        doc.put("correo", correo);
        doc.put("rol", "proveedor");
        doc.put("negocio", negocio);
        doc.put("creado", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("usuarios").document(uid).set(doc)
                .addOnSuccessListener(v -> ok.run())
                .addOnFailureListener(e -> err.accept(e.getMessage()));
    }

    /**
     * S10.3: registro de CLIENTE — un perfil por empresa (UN correo).
     * Tiene todas las funciones y no existen más usuarios que crear.
     * La empresa se guarda en "negocio" (Sesion la muestra) y también
     * en "empresa" (campo explícito del perfil).
     */
    public static void crearPerfilCliente(String uid, String nombre, String correo,
                                          String empresa, Runnable ok, Consumer<String> err) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("nombre", nombre);
        doc.put("correo", correo);
        doc.put("rol", "cliente");
        doc.put("negocio", empresa);   // para que cargarPerfil()/Sesion la recuperen
        doc.put("empresa", empresa);
        doc.put("creado", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("usuarios").document(uid).set(doc)
                .addOnSuccessListener(v -> ok.run())
                .addOnFailureListener(e -> err.accept(e.getMessage()));
    }

    /** S10: el proveedor SUBE la cuenta que el cliente le debe → Firestore. */
    public static void guardarOrden(Map<String, Object> orden,
                                    Consumer<String> alId, Consumer<String> err) {
        FirebaseFirestore.getInstance().collection("ordenes").add(orden)
                .addOnSuccessListener(ref -> alId.accept(ref.getId()))
                .addOnFailureListener(e -> err.accept(e.getMessage()));
    }

    /**
     * S10.1: ¿tiene ese correo una cuenta real en la app? Busca su perfil
     * usuarios/{uid} (lo crean la semilla y el registro). Con esto el
     * proveedor NO puede subir cuentas a un correo que no existe (typo).
     */
    public static void existeCorreo(String correo, Consumer<Boolean> alResultado,
                                    Consumer<String> err) {
        FirebaseFirestore.getInstance().collection("usuarios")
                .whereEqualTo("correo", correo).limit(1).get()
                .addOnSuccessListener(valor -> alResultado.accept(!valor.isEmpty()))
                .addOnFailureListener(e -> err.accept(e.getMessage()));
    }

    /** S10: cuentas dirigidas a un cliente, EN TIEMPO REAL (sin orderBy). */
    public static void escucharOrdenesCliente(String correo,
                                              Consumer<List<QueryDocumentSnapshot>> alLlegar) {
        FirebaseFirestore.getInstance().collection("ordenes")
                .whereEqualTo("clienteCorreo", correo)
                .addSnapshotListener((valor, error) -> {
                    if (error != null || valor == null) {
                        Log.w(TAG, "ordenes cliente: " + (error != null ? error.getMessage() : "sin datos"));
                        return;
                    }
                    alLlegar.accept(aLista(valor));
                });
    }

    /** S10 (admin): TODAS las cuentas, en tiempo real. */
    public static void escucharOrdenesTodas(Consumer<List<QueryDocumentSnapshot>> alLlegar) {
        FirebaseFirestore.getInstance().collection("ordenes")
                .addSnapshotListener((valor, error) -> {
                    if (error != null || valor == null) {
                        Log.w(TAG, "ordenes todas: " + (error != null ? error.getMessage() : "sin datos"));
                        return;
                    }
                    alLlegar.accept(aLista(valor));
                });
    }

    private static List<QueryDocumentSnapshot> aLista(com.google.firebase.firestore.QuerySnapshot valor) {
        List<QueryDocumentSnapshot> lista = new ArrayList<>();
        for (QueryDocumentSnapshot d : valor) {
            lista.add(d);
        }
        return lista;
    }

    /** S10: sube el comprobante de pago a Storage y devuelve su URL https. */
    public static void subirComprobante(String ordenId, byte[] jpeg,
                                        Consumer<String> alUrl, Consumer<String> err) {
        subirImagen("comprobantes/" + ordenId + ".jpg", jpeg, alUrl, err);
    }

    /** S10: sube la foto de una factura conciliada a Storage. */
    public static void subirFotoFactura(byte[] jpeg,
                                        Consumer<String> alUrl, Consumer<String> err) {
        subirImagen("facturas/" + System.currentTimeMillis() + ".jpg", jpeg, alUrl, err);
    }

    private static void subirImagen(String ruta, byte[] jpeg,
                                    Consumer<String> alUrl, Consumer<String> err) {
        try {
            com.google.firebase.storage.StorageReference ref =
                    com.google.firebase.storage.FirebaseStorage.getInstance()
                            .getReference().child(ruta);
            ref.putBytes(jpeg)
                    .addOnFailureListener(e -> err.accept(e.getMessage()))
                    .addOnSuccessListener(t -> ref.getDownloadUrl()
                            .addOnSuccessListener(u -> alUrl.accept(u.toString()))
                            .addOnFailureListener(e -> err.accept(e.getMessage())));
        } catch (Exception e) {
            err.accept(e.getMessage());
        }
    }

    /** S10: guarda la factura conciliada (pipeline corrido EN EL TELÉFONO). */
    public static void guardarFactura(Map<String, Object> factura,
                                      Consumer<String> alId, Consumer<String> err) {
        FirebaseFirestore.getInstance().collection("facturas").add(factura)
                .addOnSuccessListener(ref -> alId.accept(ref.getId()))
                .addOnFailureListener(e -> err.accept(e.getMessage()));
    }

    /** S10: facturas subidas por este cliente (admin: todas), en vivo. */
    public static void escucharFacturas(String correo, boolean todas,
                                        Consumer<List<QueryDocumentSnapshot>> alLlegar) {
        com.google.firebase.firestore.Query q = FirebaseFirestore.getInstance().collection("facturas");
        if (!todas) {
            q = q.whereEqualTo("subidoPor", correo);
        }
        q.addSnapshotListener((valor, error) -> {
            if (error != null || valor == null) {
                Log.w(TAG, "facturas: " + (error != null ? error.getMessage() : "sin datos"));
                return;
            }
            alLlegar.accept(aLista(valor));
        });
    }

    /** S10: promedio histórico del proveedor (alimenta el riesgo); 0 si no hay. */
    public static void historialPromedio(String proveedorSlug, Consumer<Double> alPromedio) {
        FirebaseFirestore.getInstance().collection("facturas")
                .whereEqualTo("proveedorSlug", proveedorSlug).limit(50).get()
                .addOnSuccessListener(valor -> {
                    double suma = 0;
                    int n = 0;
                    for (QueryDocumentSnapshot d : valor) {
                        Double monto = d.getDouble("monto");
                        if (monto != null && monto > 0) {
                            suma += monto;
                            n++;
                        }
                    }
                    alPromedio.accept(n == 0 ? 0.0 : suma / n);
                })
                .addOnFailureListener(e -> alPromedio.accept(0.0));
    }

    /**
     * S10: pre-check de duplicados (RF-L3): mismo folio, o la misma terna
     * monto+fecha+proveedorSlug. Devuelve el id de la ORIGINAL o null.
     */
    public static void buscarDuplicado(String folio, double monto, String fecha,
                                       String proveedorSlug,
                                       Consumer<String> alOriginal, Consumer<String> err) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        if (folio != null && !folio.isEmpty()) {
            db.collection("facturas").whereEqualTo("folio", folio).limit(1).get()
                    .addOnSuccessListener(valor -> {
                        for (QueryDocumentSnapshot d : valor) {
                            alOriginal.accept(d.getId());
                            return;
                        }
                        duplicadoPorTerna(db, monto, fecha, proveedorSlug, alOriginal, err);
                    })
                    .addOnFailureListener(e ->
                            duplicadoPorTerna(db, monto, fecha, proveedorSlug, alOriginal, err));
        } else {
            duplicadoPorTerna(db, monto, fecha, proveedorSlug, alOriginal, err);
        }
    }

    private static void duplicadoPorTerna(FirebaseFirestore db, double monto, String fecha,
                                          String proveedorSlug,
                                          Consumer<String> alOriginal, Consumer<String> err) {
        if (fecha == null || proveedorSlug == null || proveedorSlug.isEmpty()) {
            alOriginal.accept(null);
            return;
        }
        db.collection("facturas")
                .whereEqualTo("monto", monto)
                .whereEqualTo("fecha", fecha)
                .whereEqualTo("proveedorSlug", proveedorSlug)
                .limit(1).get()
                .addOnSuccessListener(valor -> {
                    for (QueryDocumentSnapshot d : valor) {
                        alOriginal.accept(d.getId());
                        return;
                    }
                    alOriginal.accept(null);
                })
                .addOnFailureListener(e -> err.accept(e.getMessage()));
    }
}
