package com.cpp.captura;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

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
 * cada arranque del proceso Y TAMBIÉN VUELVE A FALSE cuando la app se va
 * a SEGUNDO PLANO (App cuenta las pantallas vivas y llama a
 * bloquearAlFondo()): así, con la sesión dejada abierta y la app al
 * fondo, volver a ella vuelve a pedir la huella/PIN en vez de dejar
 * entrar directo (fix 22/10: antes el flag quedaba en true todo lo que
 * viviera el proceso y se entraba sin verificar).
 *
 * No se guarda ningún secreto: la verificación la hace el SISTEMA con las
 * credenciales del teléfono.
 */
public final class Candado {

    /**
     * Retardo del re-bloqueo al irse al fondo (ms): da margen para que
     * App cancele si en ese lapso volvió a haber pantalla viva (una
     * rotación o una transición interna entre pantallas propias).
     */
    private static final long RETARDO_BLOQUEO_MS = 1000;

    /** true solo tras verificar huella/PIN del teléfono en ESTE proceso. */
    private static boolean desbloqueada = false;

    /** Re-bloqueo diferido cuando la app queda sin pantallas visibles. */
    private static final Handler HILO = new Handler(Looper.getMainLooper());
    private static Runnable bloqueoPendiente = null;

    private Candado() {
        // clase utilitaria estática: no se instancia
    }

    /** Marca la app como desbloqueada (se llama SOLO tras verificar). */
    public static void desbloquear() {
        cancelarBloqueoPendiente();
        desbloqueada = true;
    }

    /** Vuelve a bloquear (app recién abierta o sesión cerrada). */
    public static void bloquear() {
        cancelarBloqueoPendiente();
        desbloqueada = false;
    }

    /**
     * La app quedó en SEGUNDO PLANO (ya no hay pantallas visibles): se
     * programa el re-bloqueo de la sesión. Con retardo para no bloquear
     * de más durante rotaciones o transiciones internas; si una pantalla
     * vuelve a estar viva antes del plazo, App cancela con
     * cancelarBloqueoPendiente().
     */
    public static void bloquearAlFondo() {
        if (bloqueoPendiente == null) {
            bloqueoPendiente = () -> {
                bloqueoPendiente = null;
                desbloqueada = false;
            };
            HILO.postDelayed(bloqueoPendiente, RETARDO_BLOQUEO_MS);
        }
    }

    /** Volvió a haber pantalla viva: el re-bloqueo programado se cancela. */
    public static void cancelarBloqueoPendiente() {
        if (bloqueoPendiente != null) {
            HILO.removeCallbacks(bloqueoPendiente);
            bloqueoPendiente = null;
        }
    }

    public static boolean estaDesbloqueada() {
        return desbloqueada;
    }

    /**
     * ¿Esta pantalla debe mandar primero al candado? Sesión vigente y
     * todavía sin verificar en este proceso (incluye entrar por una
     * notificación de recordatorio, que cae directo en PagosActivity).
     * Se revisa en onCreate Y en onResume: volver del segundo plano con
     * la sesión abierta TAMBIÉN exige la huella/PIN (fix 22/10).
     */
    public static boolean requiereCandado(Context c) {
        return Sesion.activa(c) && !desbloqueada;
    }
}
