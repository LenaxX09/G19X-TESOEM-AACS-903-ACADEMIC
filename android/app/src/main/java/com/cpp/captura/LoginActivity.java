package com.cpp.captura;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * LoginActivity â€” Sprint 10 Â«Sin servidorÂ»: la sesiÃ³n vive en FIREBASE AUTH.
 *
 * Se entra con CORREO y contraseÃ±a (nada de IP del servidor: google-services
 * apunta solo al proyecto cpp-facturas). Tras autenticar, se lee el perfil
 * usuarios/{uid} en Firestore (rol/negocio) y la app enruta como siempre:
 * proveedor â†’ su perfil; capturista/revisor/admin â†’ captura.
 *
 * Cuentas demo (clave demo1234, creadas por api/semilla_firebase.php):
 *   admin@cpp.demo Â· capturista@cpp.demo Â· revisor@cpp.demo Â· proveedor@cpp.demo
 */
public class LoginActivity extends AppCompatActivity {

    private EditText txtCorreo;
    private EditText txtPassword;
    private Button btnEntrar;
    private ProgressBar barra;
    private TextView txtResultado;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseServicio.init();
        auth = FirebaseAuth.getInstance();

        // SesiÃ³n vigente (Auth + perfil cacheado) â†’ directo a su pantalla.
        if (Sesion.activa(this)) {
            rutear();
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();   // mismo criterio 0.7.1-ui: el banner basta
        }

        txtCorreo = findViewById(R.id.txtCorreoLogin);
        txtPassword = findViewById(R.id.txtPasswordLogin);
        btnEntrar = findViewById(R.id.btnEntrar);
        barra = findViewById(R.id.barraLogin);
        txtResultado = findViewById(R.id.txtResultadoLogin);

        btnEntrar.setOnClickListener(v -> entrar());
        findViewById(R.id.btnCrearCuenta).setOnClickListener(v -> dialogoTipoCuenta());
    }

    // ========================================================================
    // Entrar (Firebase Auth) + perfil (Firestore)
    // ========================================================================

    private void entrar() {
        String correo = txtCorreo.getText().toString().trim();
        String clave = txtPassword.getText().toString();

        if (!correo.contains("@") || clave.length() < 6) {
            pintar(R.color.ambar_alerta, getString(R.string.login_faltan_datos));
            return;
        }

        barra.setVisibility(View.VISIBLE);
        pintar(R.color.texto_normal, getString(R.string.login_firebase));

        auth.signInWithEmailAndPassword(correo, clave)
                .addOnSuccessListener(resultado -> {
                    FirebaseUser u = resultado.getUser();
                    if (u == null) {
                        barra.setVisibility(View.GONE);
                        pintar(R.color.rojo_error, getString(R.string.respuesta_rara));
                        return;
                    }
                    cargarPerfil(u.getUid());
                })
                .addOnFailureListener(e -> {
                    barra.setVisibility(View.GONE);
                    pintar(R.color.rojo_error,
                            getString(R.string.login_error, e.getMessage()));
                });
    }

    /** Lee usuarios/{uid}: rol y negocio; cachea en Sesion y enruta. */
    private void cargarPerfil(String uid) {
        FirebaseServicio.cargarPerfil(uid, new FirebaseServicio.PerfilCb() {
            @Override
            public void listo(String nombre, String correo, String rol, String negocio) {
                barra.setVisibility(View.GONE);
                Sesion.guardar(LoginActivity.this, uid,
                        nombre == null ? correo : nombre,
                        correo, rol, negocio);
                rutear();
                finish();
            }

            @Override
            public void error(String mensaje) {
                barra.setVisibility(View.GONE);
                auth.signOut();   // cuenta sin perfil: no dejar media sesiÃ³n
                pintar(R.color.rojo_error,
                        getString(R.string.login_error, mensaje));
            }
        });
    }

    private void rutear() {
        if (Sesion.esProveedor(this)) {
            startActivity(new Intent(this, ProveedorActivity.class));
        } else {
            startActivity(new Intent(this, MainActivity.class));
        }
    }

    // ========================================================================
    // Crear cuenta (S8.1): proveedor público; cliente → lo crea el admin
    // ========================================================================

    private void dialogoTipoCuenta() {
        String[] opciones = {getString(R.string.tipo_cuenta_proveedor),
                getString(R.string.tipo_cuenta_cliente)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.tipo_cuenta_titulo)
                .setItems(opciones, (d, cual) -> {
                    if (cual == 0) {
                        dialogoRegistroProveedor();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.cliente_info_titulo)
                                .setMessage(R.string.cliente_info)
                                .setPositiveButton(R.string.cancelar, null)
                                .show();
                    }
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    /** Registro público de proveedor: Auth (correo/clave) + perfil Firestore. */
    private void dialogoRegistroProveedor() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_registro_proveedor, null);
        EditText edtCorreo = v.findViewById(R.id.edtRegCorreo);
        EditText edtClave = v.findViewById(R.id.edtRegPassword);
        EditText edtNegocio = v.findViewById(R.id.edtRegNegocio);

        new AlertDialog.Builder(this)
                .setTitle(R.string.reg_titulo)
                .setView(v)
                .setPositiveButton(R.string.reg_crear, (d, w) -> {
                    String correo = edtCorreo.getText().toString().trim();
                    String clave = edtClave.getText().toString();
                    String negocio = edtNegocio.getText().toString().trim();
                    if (!correo.contains("@") || clave.length() < 6 || negocio.length() < 3) {
                        pintar(R.color.ambar_alerta, getString(R.string.reg_faltan_datos));
                        return;
                    }
                    registrarProveedor(correo, clave, negocio);
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    private void registrarProveedor(String correo, String clave, String negocio) {
        barra.setVisibility(View.VISIBLE);
        pintar(R.color.texto_normal, getString(R.string.reg_creando));

        auth.createUserWithEmailAndPassword(correo, clave)
                .addOnSuccessListener(resultado -> {
                    FirebaseUser u = resultado.getUser();
                    if (u == null) {
                        barra.setVisibility(View.GONE);
                        pintar(R.color.rojo_error, getString(R.string.respuesta_rara));
                        return;
                    }
                    String nombre = correo.contains("@")
                            ? correo.substring(0, correo.indexOf('@')) : correo;
                    FirebaseServicio.crearPerfilProveedor(u.getUid(), nombre, correo, negocio,
                            () -> {
                                barra.setVisibility(View.GONE);
                                Sesion.guardar(LoginActivity.this, u.getUid(), nombre,
                                        correo, "proveedor", negocio);
                                pintar(R.color.verde_ok, getString(R.string.reg_exito, negocio));
                                startActivity(new Intent(LoginActivity.this,
                                        ProveedorActivity.class));
                                finish();
                            },
                            e -> {
                                barra.setVisibility(View.GONE);
                                pintar(R.color.rojo_error, getString(R.string.login_error, e));
                            });
                })
                .addOnFailureListener(e -> {
                    barra.setVisibility(View.GONE);
                    pintar(R.color.rojo_error, getString(R.string.login_error, e.getMessage()));
                });
    }

    // ========================================================================
    // Utilidades
    // ========================================================================

    /** Panel pastel de resultado (mismo estilo del veredicto de captura). */
    private void pintar(int colorRes, CharSequence mensaje) {
        int color = ContextCompat.getColor(this, colorRes);
        txtResultado.setVisibility(View.VISIBLE);
        txtResultado.setTextColor(color);
        txtResultado.setBackgroundTintList(ColorStateList.valueOf(
                (color & 0x00FFFFFF) | 0x33000000));
        txtResultado.setText(mensaje);
    }
}
