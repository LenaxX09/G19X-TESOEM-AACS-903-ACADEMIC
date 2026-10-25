package com.cpp.captura;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * FirebaseServicio — Sprint 10 «Sin servidor»: TODO el backend de la app.
 *
 * Firebase Auth da la identidad (correo/contraseña) y aquí vive el resto:
 *
 *   cargarPerfil()/crearPerfil*() → perfiles `usuarios/{uid}` (rol y
 *                           negocio) + directorio público
 *                           `clientesPorCorreo` (existeCorreo).
 *   guardarOrden()/escucharOrdenes*() → cuentas que el proveedor le
 *                           sube al cliente (colección `ordenes`).
 *   guardarFactura()/escucharFacturas()/historialPromedio() → facturas
 *                           conciliadas, SIEMPRE filtradas por subidoPor
 *                           (las reglas no dejan leer las ajenas) + el
 *                           índice anti-duplicados `facturasLookup`.
 *   subirFotoFactura()/subirComprobante() → imágenes en Storage.
 *   vigilarSesion()/verificarSesion() → S10.7: sesión vigilada.
 *
 * El slug normaliza el nombre del proveedor IGUAL que fb_slug() de PHP
 * (sin acentos, minúsculas, solo letras/números): "Ferretería El Tornillo"
 * → "ferreteriaeltornillo".
 *
 * Correcciones 24/10: el bloque del Sprint 6 (registrarCliente /
 * registrarProveedor / escuchar — escribía en `clientes`, `proveedores` y
 * `notificaciones`, colecciones que las reglas de Firestore CIERRAN) se
 * RETIRÓ: el proveedor vive de `ordenes` en tiempo real y los avisos S10
 * son locales (RecordatorioWorker); el push FCM queda solo para la demo
 * académica con Laragon (camino servidor).
 */
public final class FirebaseServicio {

    private static final String TAG = "CPP_S6_FB";

    private FirebaseServicio() {
        // clase utilitaria estática
    }

    /**
     * S10.3/correcciones: la sesión ANÓNIMA de los sprints 6-9 se RETIRÓ —
     * las reglas de Firestore/Storage solo aceptan usuarios con cuenta REAL
     * (correo/contraseña). El método queda como no-op para no romper a los
     * que lo llaman (LoginActivity).
     */
    public static void init() {
        // sin sesión anónima: cada usuario entra con su cuenta de Firebase Auth
    }

    /** Normaliza el nombre del proveedor → slug (espejo de fb_slug en PHP). */
    public static String slug(String proveedor) {
        String s = Normalizer.normalize(proveedor == null ? "" : proveedor,
                Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    // (correcciones 24/10) Aquí vivían registrarProveedor() y
    // proveedorGuardado() del Sprint 6: escribían/leían `proveedores` y
    // suscribían el tema FCM proveedor_<slug>. Se retiraron — las reglas
    // cierran esa colección y la app S10 no usa push de servidor (el
    // buzón del proveedor es el oyente de `ordenes` en tiempo real).

    // (correcciones 24/10) Aquí vivían la clase Noti y escuchar() del
    // Sprint 6: leían `notificaciones` (push del PHP del camino
    // servidor). Colección CERRADA por las reglas y sin llamantes en la
    // app S10 — retiradas.

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
                .addOnSuccessListener(v -> {
                    // REGLAS: además del perfil, el directorio público por
                    // correo que consulta existeCorreo() (usuarios no se lista).
                    Map<String, Object> dir = new HashMap<>();
                    dir.put("uid", uid);
                    dir.put("correo", correo);
                    FirebaseFirestore.getInstance().collection("clientesPorCorreo")
                            .document(slug(correo)).set(dir)
                            .addOnSuccessListener(x -> ok.run())
                            .addOnFailureListener(e -> err.accept(e.getMessage()));
                })
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
     * S10.1: ¿tiene ese correo una cuenta real en la app? Con las REGLAS de
     * Firestore ya NO se puede listar la colección usuarios: se consulta el
     * directorio público clientesPorCorreo/{slug} que se escribe al crear
     * el perfil (ver crearPerfilCliente y semilla_firebase.php).
     */
    public static void existeCorreo(String correo, Consumer<Boolean> alResultado,
                                    Consumer<String> err) {
        FirebaseFirestore.getInstance().collection("clientesPorCorreo")
                .document(slug(correo)).get()
                .addOnSuccessListener(d -> alResultado.accept(d.exists()))
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

    /** S10 (proveedor): SOLO las cuentas de SU negocio, en tiempo real.
     *  Con las REGLAS ya no se puede pedir la colección completa: la
     *  consulta queda filtrada por proveedorUid (la regla la exige). */
    public static void escucharOrdenesProveedor(String uid,
                                                Consumer<List<QueryDocumentSnapshot>> alLlegar) {
        FirebaseFirestore.getInstance().collection("ordenes")
                .whereEqualTo("proveedorUid", uid)
                .addSnapshotListener((valor, error) -> {
                    if (error != null || valor == null) {
                        Log.w(TAG, "ordenes proveedor: "
                                + (error != null ? error.getMessage() : "sin datos"));
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
            // REGLAS de Storage: el contentType explícito (image/jpg) es lo
            // que valida la regla (putBytes sin metadata lo manda null).
            com.google.firebase.storage.StorageMetadata meta =
                    new com.google.firebase.storage.StorageMetadata.Builder()
                            .setContentType("image/jpg").build();
            ref.putBytes(jpeg, meta)
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
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("facturas").add(factura)
                .addOnSuccessListener(ref -> {
                    // REGLAS: índice anti-duplicado (RF-L3) por folio y terna —
                    // facturas ya NO se puede listar entre clientes.
                    registrarLookups(db, ref.getId(), factura);
                    alId.accept(ref.getId());
                })
                .addOnFailureListener(e -> err.accept(e.getMessage()));
    }

    /**
     * REGLAS + RF-L3: deja constancia de la factura en facturasLookup
     * (f_<folio> y t_<monto_fecha_slug>) para que el pre-check de
     * duplicados funcione con lecturas PUNTUALES, sin listar facturas.
     * Best-effort: si falla, la factura ya quedó guardada.
     */
    private static void registrarLookups(FirebaseFirestore db, String facturaId,
                                         Map<String, Object> f) {
        try {
            String folio = (String) f.get("folio");
            Object monto = f.get("monto");
            String fecha = (String) f.get("fecha");
            String slugProv = (String) f.get("proveedorSlug");
            String dueno = (String) f.get("subidoPor");
            if (folio != null && !folio.isEmpty()) {
                escribirLookup(db, "f_" + slug(folio), facturaId, dueno);
            }
            if (fecha != null && slugProv != null && !slugProv.isEmpty() && monto != null) {
                String terna = (String.valueOf(monto) + "_" + fecha + "_" + slugProv)
                        .replaceAll("[^A-Za-z0-9_]", "");
                escribirLookup(db, "t_" + terna, facturaId, dueno);
            }
        } catch (Exception e) {
            Log.w(TAG, "lookup duplicado: " + e.getMessage());
        }
    }

    private static void escribirLookup(FirebaseFirestore db, String id,
                                       String facturaId, String dueno) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("facturaId", facturaId);
        doc.put("dueno", dueno);
        db.collection("facturasLookup").document(id).set(doc);
    }

    /**
     * S10: facturas subidas por ESTE cliente, en vivo. Siempre filtrada
     * por subidoPor (correcciones 24/10): las reglas no dejan leer
     * facturas ajenas y Firestore no filtra resultados por sí solo — una
     * consulta sin el where se rechaza completa.
     */
    public static void escucharFacturas(String correo,
                                        Consumer<List<QueryDocumentSnapshot>> alLlegar) {
        FirebaseFirestore.getInstance().collection("facturas")
                .whereEqualTo("subidoPor", correo)
                .addSnapshotListener((valor, error) -> {
                    if (error != null || valor == null) {
                        Log.w(TAG, "facturas: " + (error != null ? error.getMessage() : "sin datos"));
                        return;
                    }
                    alLlegar.accept(aLista(valor));
                });
    }

    /**
     * S10 + REGLAS (fix 22/10): promedio histórico del proveedor SOBRE
     * LAS FACTURAS DE ESTE CLIENTE — las reglas de Firestore no dejan
     * leer facturas ajenas, así que la consulta se filtra por subidoPor
     * (dueño) además de por proveedorSlug. 0 si no hay historial.
     */
    public static void historialPromedio(String correo, String proveedorSlug,
                                         Consumer<Double> alPromedio) {
        FirebaseFirestore.getInstance().collection("facturas")
                .whereEqualTo("subidoPor", correo)
                .whereEqualTo("proveedorSlug", proveedorSlug)
                .limit(50).get()
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

    // ========================================================================
    // S10.7: vigilancia de la sesión — si BORRAN la cuenta en Firebase, el
    // teléfono se entera EN VIVO y regresa al login (nada de seguir con
    // una sesión fantasma aunque la app siga abierta).
    // ========================================================================

    /** Garantiza una sola vigilancia por proceso (la pide cualquier rol). */
    private static boolean vigilando = false;

    /**
     * S10.7: la llaman las pantallas de rol (MainActivity/ProveedorActivity)
     * al crearse. Vigila DOS cosas:
     *   · usuarios/{uid}: si el perfil desaparece (la semilla y la consola
     *     borran Auth + perfil), Firestore avisa AL INSTANTE → expulsión.
     *   · Firebase Auth: si el usuario fue borrado, el SDK lo detecta al
     *     refrescar el token y firma la salida → expulsión.
     * Además, CADA llamada re-verifica contra el servidor (verificarSesion):
     * así una cuenta borrada con la app cerrada tampoco vuelve a entrar.
     * Las caídas de red NO expulsan (solo PERMISSION_DENIED o perfil fuera).
     */
    public static void vigilarSesion(Context c) {
        final String uid = Sesion.uid(c);
        if (uid.isEmpty()) {
            return;
        }
        verificarSesion(c, null);      // re-verificación en cada entrada
        if (vigilando) {
            return;                    // los oyentes viven una vez por proceso
        }
        vigilando = true;
        final Context app = c.getApplicationContext();

        FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                .addSnapshotListener((doc, error) -> {
                    if (error != null) {
                        if (error.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            expulsar(app);   // la cuenta ya no existe para el servidor
                        }
                        return;              // otra cosa (red): no expulsar
                    }
                    if (doc != null && !doc.exists()) {
                        expulsar(app);       // borraron el perfil: sesión muerta
                    }
                });

        FirebaseAuth.getInstance().addAuthStateListener(a -> {
            if (a.getCurrentUser() == null && !Sesion.uid(app).isEmpty()) {
                expulsar(app);
            }
        });
    }

    /** Cierra TODO y devuelve al login con aviso (borraron la cuenta). */
    private static void expulsar(Context app) {
        Toast.makeText(app, R.string.sesion_cuenta_borrada, Toast.LENGTH_LONG).show();
        Sesion.salirAlLogin(app);
    }

    /**
     * S10.7 (fix): verifica CONTRA EL SERVIDOR que la cuenta siga viva
     * antes de dejar entrar — el SDK cachea al usuario y el token puede
     * seguir «válido» un rato aunque la cuenta ya fue borrada.
     *   1) reload() del usuario de Auth: si fue borrado/deshabilitado,
     *      falla con FirebaseAuthInvalidUserException → expulsión.
     *   2) usuarios/{uid} debe existir (perfil borrado = cuenta fuera).
     * Las fallas de RED no expulsan ni bloquean la entrada (alContinuar).
     */
    public static void verificarSesion(Context c, Runnable alContinuar) {
        final Context app = c.getApplicationContext();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            if (!Sesion.uid(app).isEmpty()) {
                expulsar(app);
            }
            return;
        }
        u.reload()
                .addOnSuccessListener(x -> FirebaseFirestore.getInstance()
                        .collection("usuarios").document(u.getUid()).get()
                        .addOnSuccessListener(d -> {
                            if (!d.exists()) {
                                expulsar(app);            // perfil borrado
                            } else if (alContinuar != null) {
                                alContinuar.run();
                            }
                        })
                        .addOnFailureListener(e -> {
                            // sin red no podemos saber: no bloquear la entrada
                            if (alContinuar != null) {
                                alContinuar.run();
                            }
                        }))
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthInvalidUserException) {
                        expulsar(app);                    // cuenta borrada/deshabilitada
                    } else if (alContinuar != null) {
                        alContinuar.run();                // fallo de red: seguir
                    }
                });
    }

    /**
     * S10 + REGLAS: pre-check de duplicados (RF-L3) con lecturas PUNTUALES
     * al índice facturasLookup (facturas ya no se puede listar): mismo
     * folio (f_<folio>) o la misma terna monto+fecha+proveedorSlug
     * (t_<monto_fecha_slug>). Devuelve el id de la ORIGINAL o null.
     */
    public static void buscarDuplicado(String folio, double monto, String fecha,
                                       String proveedorSlug,
                                       Consumer<String> alOriginal, Consumer<String> err) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        if (folio != null && !folio.isEmpty()) {
            db.collection("facturasLookup").document("f_" + slug(folio)).get()
                    .addOnSuccessListener(d -> {
                        if (d.exists()) {
                            alOriginal.accept(d.getString("facturaId"));
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
        String id = "t_" + (String.valueOf(monto) + "_" + fecha + "_" + proveedorSlug)
                .replaceAll("[^A-Za-z0-9_]", "");
        db.collection("facturasLookup").document(id).get()
                .addOnSuccessListener(d -> alOriginal.accept(
                        d.exists() ? d.getString("facturaId") : null))
                .addOnFailureListener(e -> err.accept(e.getMessage()));
    }
}
