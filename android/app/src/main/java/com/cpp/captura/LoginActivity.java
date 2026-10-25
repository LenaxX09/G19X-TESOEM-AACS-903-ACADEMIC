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
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;

/**
 * LoginActivity — Sprint 10 «Sin servidor»: la sesión vive en FIREBASE AUTH.
 *
 * Se entra con CORREO y contraseña (nada de IP del servidor: google-services
 * apunta solo al proyecto cpp-facturas). Tras autenticar, se lee el perfil
 * usuarios/{uid} de Firestore y la app enruta: proveedor a su perfil y
 * cliente (la EMPRESA) a captura/pagos. S10.3: los roles admin/capturista/
 * revisor se unificaron en el rol único "cliente" (UN correo por empresa).
 *
 * Cuentas demo (creadas por api/semilla_firebase.php; entra con la clave
 * que configuraste en la variable de entorno CPP_DEMO_CLAVE):
 *   cliente@cpp.demo · proveedor@cpp.demo
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

        // Sesión vigente (Auth + perfil cacheado) → S10.5: primero el
        // CANDADO obligatorio (huella o contraseña), nunca directo adentro.
        if (Sesion.activa(this)) {
            Candado.bloquear();   // app recién abierta: volver a bloquear
            startActivity(new Intent(this, CandadoActivity.class));
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
                // S10.5: acaba de dar su contraseña → app desbloqueada; el
                // candado (huella/PIN del teléfono) sale en la próxima apertura.
                Candado.desbloquear();
                rutear();
                finish();
            }

            @Override
            public void error(String mensaje) {
                barra.setVisibility(View.GONE);
                auth.signOut();   // cuenta sin perfil: no dejar media sesión
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
    // Crear cuenta (S10.3): proveedor y cliente se registran SOLOS — UNA
    // cuenta (un correo) por empresa; nadie puede crear más usuarios.
    // ========================================================================

    private void dialogoTipoCuenta() {
        // S10.5: BOTONES (no lista de ítems): en el Motorola la lista del
        // diálogo no respondía al toque — con botones siempre funciona.
        new AlertDialog.Builder(this, R.style.CPPDialogo)
                .setTitle(R.string.tipo_cuenta_titulo)
                .setPositiveButton(R.string.tipo_cuenta_cliente,
                        (d, w) -> dialogoRegistroCliente())
                .setNegativeButton(R.string.tipo_cuenta_proveedor,
                        (d, w) -> dialogoRegistroProveedor())
                .setNeutralButton(R.string.cancelar, null)
                .show();
    }

    /**
     * S10.3: registro de CLIENTE — una sola cuenta (un correo) por empresa.
     * Con ella la empresa tiene TODAS las funciones (capturar, revisar,
     * pagar) y no existen más usuarios que crear: los pagos se atan a ese
     * correo y nunca se mezclan entre empresas.
     */
    private void dialogoRegistroCliente() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_registro_cliente, null);
        EditText edtCorreo = v.findViewById(R.id.edtCliCorreo);
        EditText edtClave = v.findViewById(R.id.edtCliClave);
        EditText edtEmpresa = v.findViewById(R.id.edtCliEmpresa);

        new AlertDialog.Builder(this, R.style.CPPDialogo)
                .setTitle(R.string.reg_cli_titulo)
                .setView(v)
                .setPositiveButton(R.string.reg_crear, (d, w) -> {
                    String correo = edtCorreo.getText().toString().trim();
                    String clave = edtClave.getText().toString();
                    String empresa = edtEmpresa.getText().toString().trim();
                    if (!correo.contains("@") || clave.length() < 6 || empresa.length() < 3) {
                        pintar(R.color.ambar_alerta, getString(R.string.reg_cli_faltan_datos));
                        return;
                    }
                    registrarCliente(correo, clave, empresa);
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    /** Auth (correo/clave) + perfil Firestore rol «cliente» con su empresa. */
    private void registrarCliente(String correo, String clave, String empresa) {
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
                    FirebaseServicio.crearPerfilCliente(u.getUid(), nombre, correo, empresa,
                            () -> {
                                barra.setVisibility(View.GONE);
                                Sesion.guardar(LoginActivity.this, u.getUid(), nombre,
                                        correo, "cliente", empresa);
                                pintar(R.color.verde_ok, getString(R.string.reg_cli_exito, empresa));
                                // S10.5: el candado (huella/PIN del teléfono)
                                // protege su sesión desde la próxima apertura.
                                Candado.desbloquear();
                                startActivity(new Intent(LoginActivity.this,
                                        MainActivity.class));
                                finish();
                            },
                            e -> {
                                barra.setVisibility(View.GONE);
                                pintar(R.color.rojo_error, getString(R.string.login_error, e));
                            });
                })
                .addOnFailureListener(e -> {
                    barra.setVisibility(View.GONE);
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        // S10.3: UN correo por empresa — si ya existe, se entra con él.
                        pintar(R.color.ambar_alerta, getString(R.string.reg_correo_en_uso));
                    } else {
                        pintar(R.color.rojo_error,
                                getString(R.string.login_error, e.getMessage()));
                    }
                });
    }

    /** Registro público de proveedor: Auth (correo/clave) + perfil Firestore. */
    private void dialogoRegistroProveedor() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_registro_proveedor, null);
        EditText edtCorreo = v.findViewById(R.id.edtRegCorreo);
        EditText edtClave = v.findViewById(R.id.edtRegPassword);
        EditText edtNegocio = v.findViewById(R.id.edtRegNegocio);

        new AlertDialog.Builder(this, R.style.CPPDialogo)
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
                                // S10.5: el candado (huella/PIN del teléfono)
                                // protege su sesión desde la próxima apertura.
                                Candado.desbloquear();
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
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        // S10.3: UN correo por empresa — si ya existe, se entra con él.
                        pintar(R.color.ambar_alerta, getString(R.string.reg_correo_en_uso));
                    } else {
                        pintar(R.color.rojo_error,
                                getString(R.string.login_error, e.getMessage()));
                    }
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
