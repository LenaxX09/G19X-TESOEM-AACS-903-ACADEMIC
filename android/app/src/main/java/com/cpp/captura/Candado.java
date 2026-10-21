package com.cpp.captura;

import android.content.Context;

/**
 * Candado — S10.5: bloqueo obligatorio de la app con la MISMA huella (o el
 * MISMO PIN/patrón) con la que se desbloquea el TELÉFONO.
 *
 * A petición del usuario: nada de elegir método — la app usa lo que el
 * teléfono ya tiene registrado (BiometricPrompt con BIOMETRIC_WEAK |
 * DEVICE_CREDENTIAL): pide la HUELLA si hay alguna enrolada y, si no hay
 * lector/huella, el PIN/contraseña de la pantalla de bloqueo.
 *
 * Aquí solo vive un flag en memoria «desbloqueada» que nace en false en
 * cada arranque del proceso: eso re-bloquea la app aunque Android la
 * dejara viva en segundo plano. No se guarda ningún secreto: la
 * verificación la hace el SISTEMA con las credenciales del teléfono.
 */
public final class Candado {

    /** true solo tras verificar huella/PIN del teléfono en ESTE proceso. */
    private static boolean desbloqueada = false;

    private Candado() {
        // clase utilitaria estática: no se instancia
    }

    /** Marca la app como desbloqueada (se llama SOLO tras verificar). */
    public static void desbloquear() {
        desbloqueada = true;
    }

    /** Vuelve a bloquear (app recién abierta o sesión cerrada). */
    public static void bloquear() {
        desbloqueada = false;
    }

    public static boolean estaDesbloqueada() {
        return desbloqueada;
    }

    /**
     * ¿Esta pantalla debe mandar primero al candado? Sesión vigente y
     * todavía sin verificar en este proceso (incluye entrar por una
     * notificación de recordatorio, que cae directo en PagosActivity).
     */
    public static boolean requiereCandado(Context c) {
        return Sesion.activa(c) && !desbloqueada;
    }
}
