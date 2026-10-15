package com.cpp.captura;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * LoginActivity — Sprint 5 "Acceso y roles" (RF-M1: inicio de sesión).
 *
 * Es la nueva pantalla de ENTRADA de la app (launcher): pide servidor,
 * usuario y contraseña, llama a POST /api/sesion.php y guarda la sesión
 * (token X-CPP-Token + rol) en SharedPreferences vía la clase Sesion.
 *
 * Los tres usuarios semilla del proyecto (contraseña demo1234):
 *   admin · capturista · revisor
 *
 * Si ya había una sesión guardada se salta directo a MainActivity: si el
 * token ya expiró en el servidor, la primera petición devolverá 401 y la
 * app regresa aquí automáticamente (Sesion.sinSesion → salirAlLogin).
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "CPP_S5_LOGIN";
    private static final int TIMEOUT_MS = 10000;

    private RequestQueue cola;
    private EditText txtServidor;
    private EditText txtUsuario;
    private EditText txtPassword;
    private Button btnEntrar;
    private ProgressBar barra;
    private TextView txtResultado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sesión aún vigente localmente → directo a capturar (el 401, si
        // llegara, manda de regreso aquí).
        if (Sesion.activa(this)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();   // mismo criterio 0.7.1-ui: el banner basta
        }

        cola = Volley.newRequestQueue(this);
        txtServidor = findViewById(R.id.txtServidorLogin);
        txtUsuario = findViewById(R.id.txtUsuarioLogin);
        txtPassword = findViewById(R.id.txtPasswordLogin);
        btnEntrar = findViewById(R.id.btnEntrar);
        barra = findViewById(R.id.barraLogin);
        txtResultado = findViewById(R.id.txtResultadoLogin);

        // Recuerda la última IP usada: al login solo se vuelve a teclear la clave.
        txtServidor.setText(Sesion.servidor(this));

        // Sprint 6: conexión con Firebase (auth anónima para las reglas).
        FirebaseServicio.init();

        btnEntrar.setOnClickListener(v -> entrar());
        findViewById(R.id.btnSoyProveedor).setOnClickListener(v -> cargarProveedores());
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (cola != null) {
            cola.cancelAll(TAG);
        }
    }

    // ========================================================================
    // Red — POST /api/sesion.php (login, RF-M1)
    // ========================================================================

    private void entrar() {
        String usuario = txtUsuario.getText().toString().trim();
        String password = txtPassword.getText().toString();
        if (usuario.isEmpty() || password.isEmpty()) {
            pintar(R.color.rojo_error, getString(R.string.login_faltan_datos));
            return;
        }

        JSONObject cuerpo = new JSONObject();
        try {
            cuerpo.put("usuario", usuario);
            cuerpo.put("password", password);
        } catch (Exception e) {
            pintar(R.color.rojo_error, "Error interno: " + e.getMessage());
            return;
        }

        btnEntrar.setEnabled(false);
        barra.setVisibility(View.VISIBLE);
        pintar(R.color.texto_normal, getString(R.string.login_conectando, urlBase()));

        JsonObjectRequest peticion = new JsonObjectRequest(Request.Method.POST,
                urlBase() + "/sesion.php", cuerpo,
                response -> {
                    btnEntrar.setEnabled(true);
                    barra.setVisibility(View.GONE);

                    JSONObject datos = response.optJSONObject("datos");
                    JSONObject u = datos != null ? datos.optJSONObject("usuario") : null;
                    String token = datos != null ? datos.optString("token", "") : "";
                    if ("OK".equals(response.optString("codigo")) && u != null && !token.isEmpty()) {
                        Sesion.guardar(LoginActivity.this, host(), token,
                                u.optInt("id", 0),
                                u.optString("nombre", usuario),
                                u.optString("rol", "capturista"));
                        // Sprint 6 (RF-F2): cada cliente que entra queda en Firestore.
                        FirebaseServicio.registrarCliente(u.optInt("id", 0),
                                u.optString("nombre", usuario), usuario,
                                u.optString("rol", "capturista"));
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        pintar(R.color.rojo_error, getString(R.string.respuesta_rara));
                    }
                },
                error -> {
                    btnEntrar.setEnabled(true);
                    barra.setVisibility(View.GONE);
                    // 401 = credenciales inválidas · 403 = usuario deshabilitado;
                    // ambos traen un mensaje claro del servidor en el JSON.
                    pintar(R.color.rojo_error,
                            getString(R.string.login_error, mensajeVolley(error)));
                });

        peticion.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1.0f));
        peticion.setTag(TAG);
        cola.add(peticion);
    }

    // ========================================================================
    // Sprint 6 — «🏬 Soy proveedor»: elegir proveedor y abrir el buzón
    // ========================================================================

    /** Trae los proveedores de las órdenes de compra (endpoint público). */
    private void cargarProveedores() {
        JsonObjectRequest peticion = new JsonObjectRequest(Request.Method.GET,
                urlBase() + "/proveedores.php", null,
                response -> {
                    JSONObject datos = response.optJSONObject("datos");
                    JSONArray lista = datos != null ? datos.optJSONArray("proveedores") : null;
                    if (lista == null || lista.length() == 0) {
                        pintar(R.color.ambar_alerta,
                                "El servidor no tiene proveedores registrados todavía.");
                        return;
                    }
                    final String[] nombres = new String[lista.length()];
                    for (int i = 0; i < lista.length(); i++) {
                        JSONObject p = lista.optJSONObject(i);
                        nombres[i] = p != null ? p.optString("proveedor", "?") : "?";
                    }
                    new AlertDialog.Builder(LoginActivity.this)
                            .setTitle(R.string.btn_soy_proveedor)
                            .setItems(nombres, (d, cual) ->
                                    entrarComoProveedor(nombres[cual]))
                            .setNegativeButton(R.string.cancelar, null)
                            .show();
                },
                error -> pintar(R.color.rojo_error,
                        getString(R.string.proveedor_error, mensajeVolley(error))));
        peticion.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1.0f));
        peticion.setTag(TAG);
        cola.add(peticion);
    }

    /** Registra este teléfono como receptor del proveedor y abre su buzón. */
    private void entrarComoProveedor(String proveedor) {
        FirebaseServicio.registrarProveedor(LoginActivity.this, proveedor);
        startActivity(new Intent(LoginActivity.this, ProveedorActivity.class)
                .putExtra("proveedor", proveedor));
    }

    // ========================================================================
    // Utilidades
    // ========================================================================

    /** Limpia lo tecleado: admite "10.0.2.2", "http://192.168.1.5", "192.168.1.5/". */
    private String host() {
        String h = txtServidor.getText().toString().trim();
        if (h.startsWith("http://")) h = h.substring(7);
        if (h.startsWith("https://")) h = h.substring(8);
        while (h.endsWith("/")) h = h.substring(0, h.length() - 1);
        return h.isEmpty() ? "10.0.2.2" : h;
    }

    private String urlBase() {
        return "http://" + host() + "/cpp/api";
    }

    /** Extrae el "mensaje" del JSON de error producido por responder.php, si existe. */
    private String mensajeVolley(VolleyError error) {
        NetworkResponse resp = error.networkResponse;
        if (resp != null && resp.data != null) {
            try {
                JSONObject json = new JSONObject(new String(resp.data, "UTF-8"));
                String detalle = json.optString("mensaje", null);
                if (detalle != null) {
                    return detalle;
                }
            } catch (Exception ignored) {
                // sin cuerpo JSON legible
            }
            return "HTTP " + resp.statusCode;
        }
        return error.getMessage() == null ? "sin detalles" : error.getMessage();
    }

    /**
     * Panel pastel de resultado (mismo estilo del veredicto de captura):
     * el color del estado se aplica como TINTE sobre el rectángulo blanco
     * redondeado del layout (bg_veredicto), NO como setBackgroundColor —
     * así no se pierden las esquinas redondeadas ni en modo oscuro.
     */
    private void pintar(int colorRes, CharSequence mensaje) {
        int color = ContextCompat.getColor(this, colorRes);
        txtResultado.setVisibility(View.VISIBLE);
        txtResultado.setTextColor(color);
        txtResultado.setBackgroundTintList(ColorStateList.valueOf(
                (color & 0x00FFFFFF) | 0x33000000));
        txtResultado.setText(mensaje);
    }
}
