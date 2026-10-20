package com.cpp.captura;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * App — S10.3: el tema de la app es SIEMPRE el oscuro (el que antes solo
 * salía cuando el teléfono estaba en modo noche). Así la paleta azul rey
 * se ve igual en cualquier teléfono, sin importar su configuración.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }
}
