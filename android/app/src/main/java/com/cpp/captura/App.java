package com.cpp.captura;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * App — S10.3: el tema de la app es SIEMPRE el oscuro (el que antes solo
 * salía cuando el teléfono estaba en modo noche). Así la paleta azul rey
 * se ve igual en cualquier teléfono, sin importar su configuración.
 *
 * S10.5 (fix 22/10): además cuenta las pantallas VIVAS; cuando se detiene
 * la última (la app quedó en segundo plano, aunque Android NO mate el
 * proceso) re-bloquea el candado. Así, con la sesión dejada abierta,
 * volver a la app vuelve a pedir la huella/PIN del teléfono en vez de
 * dejar entrar directo como pasaba antes (el flag «desbloqueada» quedaba
 * en true todo lo que viviera el proceso).
 */
public class App extends Application {

    /** Pantallas arrancadas y sin detener (0 = app en segundo plano). */
    private int pantallasVivas = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        registrarCandadoDeFondo();
    }

    /**
     * Cuenta los onStart/onStop de TODAS las actividades del proceso:
     * al llegar a 0 la app quedó en segundo plano → se programa el
     * re-bloqueo del candado (con retardo: una rotación o transición
     * interna hace que al instante vuelva a haber pantalla y se cancele).
     */
    private void registrarCandadoDeFondo() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity actividad) {
                pantallasVivas++;
                Candado.cancelarBloqueoPendiente();
            }

            @Override
            public void onActivityStopped(Activity actividad) {
                pantallasVivas = Math.max(0, pantallasVivas - 1);
                if (pantallasVivas == 0) {
                    Candado.bloquearAlFondo();
                }
            }

            @Override
            public void onActivityCreated(Activity actividad, Bundle estado) { }

            @Override
            public void onActivityResumed(Activity actividad) { }

            @Override
            public void onActivityPaused(Activity actividad) { }

            @Override
            public void onActivitySaveInstanceState(Activity actividad, Bundle salida) { }

            @Override
            public void onActivityDestroyed(Activity actividad) { }
        });
    }
}
