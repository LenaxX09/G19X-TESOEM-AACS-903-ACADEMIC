package com.cpp.captura;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * AdminActivity — Sprint 5 "Acceso y roles" (RF-B4 completo: CRUD).
 *
 * Pantalla de administración EXCLUSIVA del rol admin (la propia API la
 * respalda con 403 si un rol menor llama a usuarios.php; aquí además se
 * corta por defecto). Un selector (Spinner) decide qué se administra:
 *
 *   · Usuarios ........ crear / editar (nombre, rol, contraseña, activo).
 *                       La "baja" es lógica (activo=0): nunca se borran
 *                       usuarios con historial. La API además protege el
 *                       último admin activo (409 ULTIMO_ADMIN).
 *   · Órdenes ......... crear / editar (código, proveedor, monto, fecha,
 *                       abierta/cerrada). «Cerrar» es su baja lógica: no hay
 *                       DELETE físico porque las facturas las referencian (FK).
 *
 * Formularios en diálogos (dialog_usuario.xml / dialog_orden.xml), sin
 * dependencias nuevas: mismo criterio del proyecto desde la 0.6.0-ui.
 */
public class AdminActivity extends AppCompatActivity {

    private static final String TAG = "CPP_S5_ADMIN";
    private static final int TIMEOUT_MS = 15000;
    private static final int MODO_USUARIOS = 0;
    private static final int MODO_ORDENES = 1;

    private RequestQueue cola;
    private RecyclerView reciclador;
    private ProgressBar barra;
    private TextView txtEstado;
    private Spinner spModo;
    private int modo = MODO_USUARIOS;
    private AdminAdapter adaptador;

    private final List<FilaAdmin> filas = new ArrayList<>();
    // Sprint 7 (RF-F5): 'proveedor' se gestiona igual desde el CRUD del admin.
    private final List<String> roles = Arrays.asList("capturista", "revisor", "admin", "proveedor");

    /** Datos de una fila (usuario u orden) + el JSON crudo para prellenar el diálogo. */
    private static class FilaAdmin {
        final int id;
        final String titulo;
        final String sub;
        final String estado;
        final boolean activo;
        final JSONObject crudo;

        FilaAdmin(int id, String titulo, String sub, String estado,
                  boolean activo, JSONObject crudo) {
            this.id = id;
            this.titulo = titulo;
            this.sub = sub;
            this.estado = estado;
            this.activo = activo;
            this.crudo = crudo;
        }
    }

    // ========================================================================
    // Ciclo de vida
    // ========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Defensa en profundidad: la API también responde 403 (RF-B6).
        if (!Sesion.esAdmin(this)) {
            Toast.makeText(this, R.string.admin_solo_admin, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ActionBar barraApp = getSupportActionBar();
        if (barraApp != null) {
            barraApp.setDisplayHomeAsUpEnabled(true);      // ⬅ volver a captura
            barraApp.setDisplayShowTitleEnabled(false);
        }

        cola = Volley.newRequestQueue(this);
        reciclador = findViewById(R.id.recAdmin);
        barra = findViewById(R.id.barraAdmin);
        txtEstado = findViewById(R.id.txtEstadoAdmin);
        spModo = findViewById(R.id.spModo);
        Button btnNuevo = findViewById(R.id.btnNuevo);
        Button btnActualizar = findViewById(R.id.btnActualizarAdmin);

        ArrayAdapter<String> modos = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                Arrays.asList(getString(R.string.modo_usuarios), getString(R.string.modo_ordenes)));
        modos.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spModo.setAdapter(modos);
        spModo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                modo = pos;
                cargar();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no ocurre con un Spinner con elementos fijos
            }
        });

        btnNuevo.setOnClickListener(v -> {
            if (modo == MODO_USUARIOS) {
                dialogUsuario(null);
            } else {
                dialogOrden(null);
            }
        });
        btnActualizar.setOnClickListener(v -> cargar());

        adaptador = new AdminAdapter();
        reciclador.setLayoutManager(new LinearLayoutManager(this));
        reciclador.setAdapter(adaptador);
    }

    @Override
    protected void onDestroy() {
        if (cola != null) {
            cola.cancelAll(TAG);
        }
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();     // ⬅ de la ActionBar → regresar a MainActivity
        return true;
    }

    private String urlBase() {
        return "http://" + Sesion.servidor(this) + "/cpp/api";
    }

    // ========================================================================
    // Red — GET usuarios.php / ordenes.php (según el modo del Spinner)
    // ========================================================================

    private void cargar() {
        String url = urlBase() + (modo == MODO_USUARIOS ? "/usuarios.php" : "/ordenes.php");
        barra.setVisibility(View.VISIBLE);
        txtEstado.setVisibility(View.VISIBLE);
        txtEstado.setTextColor(ContextCompat.getColor(this, R.color.texto_normal));
        txtEstado.setText(getString(R.string.admin_cargando, url));

        JsonObjectRequest peticion = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    barra.setVisibility(View.GONE);
                    filas.clear();
                    JSONObject datos = response.optJSONObject("datos");
                    JSONArray lista = null;
                    if (modo == MODO_USUARIOS) {
                        lista = datos != null ? datos.optJSONArray("usuarios") : null;
                    } else {
                        lista = datos != null ? datos.optJSONArray("ordenes") : null;
                    }
                    if (lista != null) {
                        for (int i = 0; i < lista.length(); i++) {
                            JSONObject o = lista.optJSONObject(i);
                            if (o == null) {
                                continue;
                            }
                            filas.add(modo == MODO_USUARIOS ? filaUsuario(o) : filaOrden(o));
                        }
                    }
                    adaptador.notifyDataSetChanged();
                    txtEstado.setVisibility(filas.isEmpty() ? View.VISIBLE : View.GONE);
                    txtEstado.setText(R.string.admin_sin_datos);
                },
                error -> {
                    barra.setVisibility(View.GONE);
                    filas.clear();
                    adaptador.notifyDataSetChanged();
                    if (Sesion.sinSesion(error)) {
                        Toast.makeText(AdminActivity.this,
                                R.string.sesion_caducada, Toast.LENGTH_LONG).show();
                        Sesion.salirAlLogin(AdminActivity.this);
                        return;
                    }
                    txtEstado.setVisibility(View.VISIBLE);
                    txtEstado.setTextColor(ContextCompat.getColor(this, R.color.rojo_error));
                    txtEstado.setText(getString(R.string.admin_error, mensajeVolley(error)));
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>(super.getHeaders());
                h.putAll(Sesion.cabeceras(AdminActivity.this));
                return h;
            }
        };

        peticion.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1.0f));
        peticion.setTag(TAG);
        cola.add(peticion);
    }

    private FilaAdmin filaUsuario(JSONObject u) {
        boolean activo = u.optInt("activo", 0) == 1;
        String sub = getString(R.string.fila_usuario_sub, u.optString("usuario", "?"),
                u.optString("rol", "?"));
        String negocio = u.optString("negocio", "");            // Sprint 7 (RF-F5)
        if (!negocio.isEmpty() && !"null".equals(negocio)) {
            sub += " · " + negocio;
        }
        return new FilaAdmin(
                u.optInt("id", 0),
                u.optString("nombre", "?"),
                sub,
                activo ? getString(R.string.estado_activo) : getString(R.string.estado_inactivo),
                activo, u);
    }

    private FilaAdmin filaOrden(JSONObject o) {
        boolean abierta = "abierta".equals(o.optString("estado", "abierta"));
        return new FilaAdmin(
                o.optInt("id", 0),
                o.optString("codigo", "?"),
                getString(R.string.fila_orden_sub, o.optString("proveedor", "?"),
                        o.optString("monto_autorizado", "0"),
                        o.optString("fecha_emision", "?")),
                abierta ? getString(R.string.orden_abierta) : getString(R.string.orden_cerrada),
                abierta, o);
    }

    // ========================================================================
    // Adaptador de la lista
    // ========================================================================

    private class AdminAdapter extends RecyclerView.Adapter<AdminAdapter.Fila> {

        @NonNull
        @Override
        public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
            View v = LayoutInflater.from(padre.getContext())
                    .inflate(R.layout.fila_admin, padre, false);
            return new Fila(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Fila fila, int pos) {
            FilaAdmin f = filas.get(pos);
            fila.titulo.setText(f.titulo);
            fila.sub.setText(f.sub);
            fila.estado.setText(f.estado);
            fila.estado.setTextColor(ContextCompat.getColor(AdminActivity.this,
                    f.activo ? R.color.verde_ok : R.color.rojo_error));
            fila.itemView.setOnClickListener(v -> {
                if (modo == MODO_USUARIOS) {
                    dialogUsuario(f);
                } else {
                    dialogOrden(f);
                }
            });
        }

        @Override
        public int getItemCount() {
            return filas.size();
        }

        class Fila extends RecyclerView.ViewHolder {
            final TextView titulo;
            final TextView sub;
            final TextView estado;

            Fila(View v) {
                super(v);
                titulo = v.findViewById(R.id.txtFilaTitulo);
                sub = v.findViewById(R.id.txtFilaSub);
                estado = v.findViewById(R.id.txtFilaEstado);
            }
        }
    }

    // ========================================================================
    // Diálogo USUARIO — crear (f == null) o editar (f != null)
    // ========================================================================
    private void dialogUsuario(@androidx.annotation.Nullable FilaAdmin f) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_usuario, null);
        EditText edtNombre = v.findViewById(R.id.edtNombre);
        EditText edtLogin = v.findViewById(R.id.edtUsuarioLogin);
        EditText edtPassword = v.findViewById(R.id.edtPassword);
        Spinner spRol = v.findViewById(R.id.spRol);
        EditText edtNegocio = v.findViewById(R.id.edtNegocio);   // Sprint 7 (RF-F5)
        Switch swActivo = v.findViewById(R.id.swActivo);

        spRol.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, roles));
        swActivo.setChecked(true);

        if (f != null) {
            edtNombre.setText(f.crudo.optString("nombre", ""));
            edtLogin.setText(f.crudo.optString("usuario", ""));
            edtLogin.setEnabled(false);                      // el login no se cambia
            edtPassword.setHint(R.string.hint_password_editar);
            int pos = roles.indexOf(f.crudo.optString("rol", "capturista"));
            spRol.setSelection(pos >= 0 ? pos : 0);
            String negocio = f.crudo.optString("negocio", "");
            if (!"null".equals(negocio)) {
                edtNegocio.setText(negocio);
            }
            swActivo.setChecked(f.activo);
        }

        new AlertDialog.Builder(this)
                .setTitle(f == null ? R.string.dlg_usuario_nuevo : R.string.dlg_usuario_editar)
                .setView(v)
                .setPositiveButton(R.string.guardar, (d, w) -> guardarUsuario(f, edtNombre,
                        edtLogin, edtPassword, spRol, edtNegocio, swActivo))
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    private void guardarUsuario(FilaAdmin f, EditText edtNombre, EditText edtLogin,
                                EditText edtPassword, Spinner spRol, EditText edtNegocio,
                                Switch swActivo) {
        String nombre = edtNombre.getText().toString().trim();
        String rol = roles.get(Math.max(0, spRol.getSelectedItemPosition()));
        String password = edtPassword.getText().toString();

        if (nombre.isEmpty()) {
            Toast.makeText(this, R.string.admin_falta_nombre, Toast.LENGTH_SHORT).show();
            return;
        }

        // Sprint 7 (RF-F5): el rol proveedor SIEMPRE viaja con su negocio.
        String negocio = edtNegocio.getText().toString().trim();
        if ("proveedor".equals(rol) && negocio.isEmpty()) {
            Toast.makeText(this, R.string.admin_falta_negocio, Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject cuerpo = new JSONObject();
        try {
            if (f == null) {
                String login = edtLogin.getText().toString().trim();
                if (login.isEmpty() || password.length() < 6) {
                    Toast.makeText(this, R.string.admin_falta_usuario_pass, Toast.LENGTH_SHORT).show();
                    return;
                }
                cuerpo.put("accion", "crear");
                cuerpo.put("nombre", nombre);
                cuerpo.put("usuario", login);
                cuerpo.put("password", password);
                cuerpo.put("rol", rol);
                if ("proveedor".equals(rol)) {
                    cuerpo.put("negocio", negocio);
                }
            } else {
                cuerpo.put("accion", "actualizar");
                cuerpo.put("id", f.id);
                cuerpo.put("nombre", nombre);
                cuerpo.put("rol", rol);
                cuerpo.put("activo", swActivo.isChecked() ? 1 : 0);
                if ("proveedor".equals(rol)) {
                    cuerpo.put("negocio", negocio);
                }
                if (!password.isEmpty()) {
                    cuerpo.put("password", password);   // vacío = no cambiarla
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error interno: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        enviar("usuarios.php", cuerpo);
    }

    // ========================================================================
    // Diálogo ORDEN — crear (f == null) o editar (f != null)
    // ========================================================================
    private void dialogOrden(@androidx.annotation.Nullable FilaAdmin f) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_orden, null);
        EditText edtCodigo = v.findViewById(R.id.edtCodigo);
        EditText edtProveedor = v.findViewById(R.id.edtProveedor);
        EditText edtMonto = v.findViewById(R.id.edtMonto);
        EditText edtFecha = v.findViewById(R.id.edtFecha);
        Spinner spEstado = v.findViewById(R.id.spEstadoOrden);

        spEstado.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                Arrays.asList("abierta", "cerrada")));

        if (f != null) {
            edtCodigo.setText(f.crudo.optString("codigo", ""));
            edtProveedor.setText(f.crudo.optString("proveedor", ""));
            edtMonto.setText(f.crudo.optString("monto_autorizado", ""));
            edtFecha.setText(f.crudo.optString("fecha_emision", ""));
            spEstado.setSelection("cerrada".equals(f.crudo.optString("estado")) ? 1 : 0);
        }

        new AlertDialog.Builder(this)
                .setTitle(f == null ? R.string.dlg_orden_nueva : R.string.dlg_orden_editar)
                .setView(v)
                .setPositiveButton(R.string.guardar, (d, w) -> guardarOrden(f, edtCodigo,
                        edtProveedor, edtMonto, edtFecha, spEstado))
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    private void guardarOrden(FilaAdmin f, EditText edtCodigo, EditText edtProveedor,
                              EditText edtMonto, EditText edtFecha, Spinner spEstado) {
        String codigo = edtCodigo.getText().toString().trim();
        String proveedor = edtProveedor.getText().toString().trim();
        String montoTxt = edtMonto.getText().toString().trim();
        String fecha = edtFecha.getText().toString().trim();

        double monto;
        try {
            monto = Double.parseDouble(montoTxt);
        } catch (NumberFormatException e) {
            monto = 0;
        }
        if (codigo.isEmpty() || proveedor.isEmpty() || monto <= 0
                || !fecha.matches("\\d{4}-\\d{2}-\\d{2}")) {
            Toast.makeText(this, R.string.admin_falta_orden, Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject cuerpo = new JSONObject();
        try {
            cuerpo.put("accion", f == null ? "crear" : "actualizar");
            if (f != null) {
                cuerpo.put("id", f.id);
            }
            cuerpo.put("codigo", codigo);
            cuerpo.put("proveedor", proveedor);
            cuerpo.put("monto_autorizado", monto);
            cuerpo.put("fecha_emision", fecha);
            cuerpo.put("estado", spEstado.getSelectedItemPosition() == 1 ? "cerrada" : "abierta");
        } catch (Exception e) {
            Toast.makeText(this, "Error interno: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        enviar("ordenes.php", cuerpo);
    }

    // ========================================================================
    // Envío común — POST usuarios.php / ordenes.php con el token
    // ========================================================================
    private void enviar(String endpoint, JSONObject cuerpo) {
        JsonObjectRequest peticion = new JsonObjectRequest(Request.Method.POST,
                urlBase() + "/" + endpoint, cuerpo,
                response -> {
                    Toast.makeText(this, response.optString("mensaje", "OK"),
                            Toast.LENGTH_LONG).show();
                    cargar();   // refresca la lista con lo nuevo
                },
                error -> {
                    if (Sesion.sinSesion(error)) {
                        Toast.makeText(AdminActivity.this,
                                R.string.sesion_caducada, Toast.LENGTH_LONG).show();
                        Sesion.salirAlLogin(AdminActivity.this);
                        return;
                    }
                    Toast.makeText(AdminActivity.this,
                            getString(R.string.admin_error, mensajeVolley(error)),
                            Toast.LENGTH_LONG).show();
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>(super.getHeaders());
                h.putAll(Sesion.cabeceras(AdminActivity.this));
                return h;
            }
        };

        peticion.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1.0f));
        peticion.setTag(TAG);
        cola.add(peticion);
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
}
