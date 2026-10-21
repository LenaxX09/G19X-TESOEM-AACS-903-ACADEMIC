package com.cpp.captura;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Sesion â€” Sprint 10 Â«Sin servidorÂ»: la sesiÃ³n vive en FIREBASE AUTH.
 *
 * Antes (S5-S9) la app guardaba servidor + token X-CPP-Token y cada peticiÃ³n
 * iba al PHP de Laragon. Ahora:
 *   Â· La identidad la maneja FirebaseAuth (correo/contraseÃ±a); no hay IP que
 *     teclear ni token propio: google-services.json apunta al proyecto.
 *   Â· AquÃ­ solo se cachea el PERFIL (usuarios/{uid} de Firestore): nombre,
 *     correo, rol y negocio, para enrutar la app igual que antes.
 *
 * S10.3: los roles admin/capturista/revisor SE UNIFICARON en «cliente»:
 * cada EMPRESA cliente tiene UNA cuenta (un correo) con todas las funciones
 * y nadie puede crear más usuarios (los pagos se atan a ese correo).
 * esProveedor()/negocio()/salirAlLogin() se conservan.
 */
public final class Sesion {

    private static final String PREFS = "cpp_sesion";
    private static final String K_UID    = "uid";
    private static final String K_NOMBRE = "nombre";
    private static final String K_CORREO = "correo";
    private static final String K_ROL    = "rol";
    private static final String K_NEGOCIO = "negocio";   // proveedor: su negocio · cliente: su empresa

    private Sesion() {
        // clase utilitaria estÃ¡tica: no se instancia
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Cachea el perfil leÃ­do de Firestore usuarios/{uid} tras el login. */
    public static void guardar(Context c, String uid, String nombre, String correo,
                               String rol, String negocio) {
        prefs(c).edit()
                .putString(K_UID, uid)
                .putString(K_NOMBRE, nombre)
                .putString(K_CORREO, correo)
                .putString(K_ROL, rol)
                .putString(K_NEGOCIO, negocio == null ? "" : negocio)
                .apply();
    }

    public static String uid(Context c) {
        return prefs(c).getString(K_UID, "");
    }

    public static String nombre(Context c) {
        return prefs(c).getString(K_NOMBRE, "");
    }

    public static String correo(Context c) {
        return prefs(c).getString(K_CORREO, "");
    }

    public static String rol(Context c) {
        return prefs(c).getString(K_ROL, "");
    }

    public static String negocio(Context c) {
        return prefs(c).getString(K_NEGOCIO, "");
    }

    /** Â¿Hay perfil cacheado Y sesiÃ³n vigente en Firebase Auth? */
    public static boolean activa(Context c) {
        if (prefs(c).getString(K_UID, "").isEmpty()) {
            return false;
        }
        try {
            return FirebaseAuth.getInstance().getCurrentUser() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** S10.3: rol único de empresa — todo lo que NO es proveedor es CLIENTE. */
    public static boolean esCliente(Context c) {
        return !esProveedor(c);
    }

    public static boolean esProveedor(Context c) {
        return "proveedor".equals(rol(c));
    }

    /** Borra el perfil local (logout o caducidad). */
    public static void cerrar(Context c) {
        prefs(c).edit().clear().apply();
    }

    /** Cierra Firebase Auth, limpia el perfil y regresa al login. */
    public static void salirAlLogin(Context c) {
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception ignored) {
            // sin FirebaseApp (p. ej. pruebas de instrumentaciÃ³n): seguir
        }
        cerrar(c);
        Intent i = new Intent(c, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        c.startActivity(i);
    }

    /**
     * S10.5: abre la pantalla que corresponde al rol del perfil cacheado
     * (proveedor → su buzón; cliente → captura/pagos). La usa el CANDADO
     * al verificarse y el login tras enrutar.
     */
    public static void abrirPantallaSegunRol(Context c) {
        Intent i = new Intent(c, esProveedor(c)
                ? ProveedorActivity.class : MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        c.startActivity(i);
    }
}
