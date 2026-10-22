package com.cpp.captura;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

/**
 * CandadoActivity — S10.5: candado obligatorio de la app con la HUELLA (o el
 * PIN/patrón) del TELÉFONO — los mismos con los que se desbloquea el celular.
 *
 * Nada que elegir ni configurar (decisión del usuario): BiometricPrompt con
 * BIOMETRIC_WEAK | DEVICE_CREDENTIAL pide la huella registrada del teléfono
 * y, si el teléfono no tiene lector/huella, el PIN/contraseña de la pantalla
 * de bloqueo. La verificación la hace el SISTEMA: la app nunca ve ni guarda
 * ningún secreto.
 *
 * Se llega aquí siempre que la app se abre con sesión vigente (Login
 * enruta aquí en vez de a la pantalla del rol) y también al tocar una
 * notificación de recordatorio (las pantallas internas revisan
 * Candado.requiereCandado). «Cerrar sesión» queda como válvula de escape.
 */
public class CandadoActivity extends AppCompatActivity {

    private TextView txtResultado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sin sesión vigente no hay nada que proteger: al login.
        if (!Sesion.activa(this)) {
            Sesion.salirAlLogin(this);
            finish();
            return;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        ((TextView) findViewById(R.id.txtCandadoCorreo)).setText(Sesion.correo(this));
        txtResultado = findViewById(R.id.txtResultadoCandado);
        findViewById(R.id.btnCandadoReintentar).setOnClickListener(
                v -> pedirVerificacion());
        findViewById(R.id.btnCandadoSalir).setOnClickListener(v -> {
            Candado.bloquear();
            Sesion.salirAlLogin(CandadoActivity.this);
        });

        pedirVerificacion();
    }

    /** El botón ATRÁS no puede saltarse el candado: manda la app al fondo. */
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    /**
     * Lanza el BiometricPrompt del SISTEMA: huella del teléfono y, si no
     * hay, su PIN/patrón de bloqueo. Sin botón negativo propio (prohibido
     * al combinar con DEVICE_CREDENTIAL): el sistema ya pone «Cancelar».
     */
    private void pedirVerificacion() {
        int puede = BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL);

        if (puede != BiometricManager.BIOMETRIC_SUCCESS) {
            // Teléfono SIN huella y SIN PIN: no hay con qué verificar.
            pintar(R.color.ambar_alerta, getString(R.string.candado_sin_bloqueo));
            return;   // quedan «Volver a intentar» (cuando configure) y «Cerrar sesión»
        }

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.candado_biometrico_titulo))
                .setSubtitle(getString(R.string.candado_biometrico_sub))
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
                                | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult resultado) {
                        Candado.desbloquear();
                        Sesion.abrirPantallaSegunRol(CandadoActivity.this);
                        finish();
                    }

                    @Override
                    public void onAuthenticationError(int codigo, @NonNull CharSequence mensaje) {
                        // Cancelar no es error grave: solo invita a reintentar.
                        if (codigo == BiometricPrompt.ERROR_USER_CANCELED
                                || codigo == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            pintar(R.color.ambar_alerta,
                                    getString(R.string.candado_cancelado));
                        } else {
                            pintar(R.color.rojo_error, String.valueOf(mensaje));
                        }
                    }
                }).authenticate(info);
    }

    /** Panel pastel de resultado (mismo estilo del login). */
    private void pintar(int colorRes, CharSequence mensaje) {
        int color = ContextCompat.getColor(this, colorRes);
        txtResultado.setVisibility(android.view.View.VISIBLE);
        txtResultado.setTextColor(color);
        txtResultado.setBackgroundTintList(ColorStateList.valueOf(
                (color & 0x00FFFFFF) | 0x33000000));
        txtResultado.setText(mensaje);
    }
}
