package com.cpp.captura;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.android.volley.NetworkResponse;
import com.android.volley.VolleyError;

import java.util.HashMap;
import java.util.Map;

/**
 * Sesion — Sprint 5 "Acceso y roles" (RF-M1 / RF-B6).
 *
 * Guarda en SharedPreferences TODO lo que la app necesita saber de la sesión
 * iniciada: servidor, token X-CPP-Token, y el usuario (id, nombre, rol).
 * Antes de la v1.0 la app no sabía QUIÉN enviaba la factura; ahora cada
 * petición sale firmada con el token y la API decide qué rol puede hacer qué.
 *
 * Puntos clave:
 *   · cabeceras()  → el Map que Volley manda en cada petición autenticada.
 *   · sinSesion()  → ¿el servidor respondió 401? (token expirado/cerrado).
 *   · salirAlLogin → limpia la sesión y vuelve a la pantalla de login.
 *
 * Deuda honesta (v1.1): el token viaja en prefs SIN cifrar. En el entorno
 * local de demo es aceptable; para producción se usará EncryptedSharedPreferences
 * (requiere dependencia nueva, y la regla del proyecto es no agregar dependencias).
 */
public final class Sesion {

    private static final String PREFS = "cpp_sesion";
    private static final String K_SERVIDOR = "servidor";
    private static final String K_TOKEN    = "token";
    private static final String K_ID       = "usuario_id";
    private static final String K_NOMBRE   = "nombre";
    private static final String K_ROL      = "rol";

    private Sesion() {
        // clase utilitaria estática: no se instancia
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Guarda la sesión completa tras un login exitoso. */
    public static void guardar(Context c, String servidor, String token,
                               int usuarioId, String nombre, String rol) {
        prefs(c).edit()
                .putString(K_SERVIDOR, servidor)
                .putString(K_TOKEN, token)
                .putInt(K_ID, usuarioId)
                .putString(K_NOMBRE, nombre)
                .putString(K_ROL, rol)
                .apply();
    }

    /** Guarda solo la IP del servidor (MainActivity permite editarla al vuelo). */
    public static void setServidor(Context c, String servidor) {
        prefs(c).edit().putString(K_SERVIDOR, servidor).apply();
    }

    public static String servidor(Context c) {
        return servidor(c, "10.0.2.2");   // mismo default histórico: alias del host desde el emulador
    }

    public static String servidor(Context c, String porDefecto) {
        return prefs(c).getString(K_SERVIDOR, porDefecto);
    }

    public static String token(Context c) {
        return prefs(c).getString(K_TOKEN, "");
    }

    public static String nombre(Context c) {
        return prefs(c).getString(K_NOMBRE, "");
    }

    public static String rol(Context c) {
        return prefs(c).getString(K_ROL, "");
    }

    public static int id(Context c) {
        return prefs(c).getInt(K_ID, 0);
    }

    /** ¿Hay un token guardado? (si expiró en el servidor, el primer 401 regresa al login). */
    public static boolean activa(Context c) {
        return !token(c).isEmpty();
    }

    public static boolean esAdmin(Context c) {
        return "admin".equals(rol(c));
    }

    public static boolean esRevisor(Context c) {
        return "revisor".equals(rol(c));
    }

    /** Cabeceras HTTP para toda petición autenticada (RF-M1). */
    public static Map<String, String> cabeceras(Context c) {
        Map<String, String> h = new HashMap<>();
        String t = token(c);
        if (!t.isEmpty()) {
            h.put("X-CPP-Token", t);
        }
        return h;
    }

    /** ¿La respuesta fue 401 NO_AUTENTICADO? → hay que volver a iniciar sesión. */
    public static boolean sinSesion(VolleyError error) {
        NetworkResponse r = error.networkResponse;
        return r != null && r.statusCode == 401;
    }

    /** Borra la sesión local (logout o caducidad). */
    public static void cerrar(Context c) {
        prefs(c).edit().clear().apply();
    }

    /** Cierra y regresa al login limpiando la pila de actividades. */
    public static void salirAlLogin(Context c) {
        cerrar(c);
        Intent i = new Intent(c, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        c.startActivity(i);
    }
}
