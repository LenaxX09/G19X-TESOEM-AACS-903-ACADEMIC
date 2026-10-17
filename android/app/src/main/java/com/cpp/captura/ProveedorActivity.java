package com.cpp.captura;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ProveedorActivity — Sprint 6 (RF-F3/F4): el "buzón" del proveedor.
 *
 * Se llega desde el botón «🏬 Soy proveedor» del login: el teléfono queda
 * asociado al proveedor elegido (Firestore `proveedores` + tema FCM) y esta
 * pantalla escucha EN TIEMPO REAL la colección `notificaciones`: en cuanto
 * alguien sube una factura de ese proveedor, aparece aquí al instante —
 * sin botón de actualizar. Con la app cerrada, el aviso llega como push
 * del sistema (lo envía api/facturas.php vía FCM y lo muestra PushServicio).
 */
public class ProveedorActivity extends AppCompatActivity {

    private static final int REQ_NOTIFICACIONES = 2001;

    private String proveedor;
    private NotisAdapter adaptador;
    private RequestQueue cola;                       // Sprint 7: logout con el token
    private final List<FirebaseServicio.Noti> notis = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_proveedor);

        proveedor = getIntent().getStringExtra("proveedor");
        // Sprint 7 (RF-F7): con cuenta propia, el negocio viene de la sesión.
        if ((proveedor == null || proveedor.isEmpty()) && Sesion.esProveedor(this)) {
            proveedor = Sesion.negocio(this);
        }
        if (proveedor == null || proveedor.isEmpty()) {
            proveedor = FirebaseServicio.proveedorGuardado(this);
        }
        if (proveedor == null || proveedor.isEmpty()) {
            finish();
            return;
        }

        ActionBar barraApp = getSupportActionBar();
        if (barraApp != null) {
            barraApp.setDisplayHomeAsUpEnabled(true);
            barraApp.setDisplayShowTitleEnabled(false);
        }

        TextView txtSub = findViewById(R.id.txtSubProveedor);
        txtSub.setText(getString(R.string.subtitulo_proveedor, proveedor));

        RecyclerView rec = findViewById(R.id.recNotificaciones);
        adaptador = new NotisAdapter();
        rec.setLayoutManager(new LinearLayoutManager(this));
        rec.setAdapter(adaptador);

        TextView txtEstado = findViewById(R.id.txtEstadoProveedor);
        txtEstado.setText(R.string.proveedor_escuchando);

        Button btnSalir = findViewById(R.id.btnSalirProveedor);
        cola = Volley.newRequestQueue(this);

        if (Sesion.esProveedor(this)) {
            // Sprint 7 (RF-F7): perfil EXCLUSIVO con cuenta — puede ENVIAR sus
            // facturas (MainActivity cargará solo SUS órdenes vía la API) y
            // cerrar sesión de verdad (borra el token en el servidor).
            Button btnEnviar = findViewById(R.id.btnEnviarFactura);
            btnEnviar.setVisibility(View.VISIBLE);
            btnEnviar.setOnClickListener(v ->
                    startActivity(new Intent(this, MainActivity.class)));
            btnSalir.setText(R.string.proveedor_cerrar_sesion);
            btnSalir.setOnClickListener(v -> cerrarSesion());
        } else {
            // Modo rápido «Soy proveedor» (S6): solo mirar el buzón.
            btnSalir.setOnClickListener(v -> finish());
        }

        // Android 13+: pedir permiso para que el PUSH se vea (no el listener).
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICACIONES);
        }

        // RF-F4: oyente en tiempo real (app abierta).
        FirebaseServicio.escuchar(proveedor, lista -> runOnUiThread(() -> {
            notis.clear();
            notis.addAll(lista);
            adaptador.notifyDataSetChanged();
            txtEstado.setText(notis.isEmpty()
                    ? getString(R.string.proveedor_sin_facturas)
                    : getString(R.string.proveedor_con_facturas, notis.size()));
        }));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();     // ⬅ volver al login
        return true;
    }

    /** Sprint 7 (RF-F5): logout real — DELETE /sesion.php con el token y al login. */
    private void cerrarSesion() {
        JsonObjectRequest peticion = new JsonObjectRequest(Request.Method.DELETE,
                "http://" + Sesion.servidor(this) + "/cpp/api/sesion.php", null,
                r -> Sesion.salirAlLogin(ProveedorActivity.this),
                e -> Sesion.salirAlLogin(ProveedorActivity.this)) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                // Copia mutable: super.getHeaders() es inmutable (hotfix 0.8.2).
                Map<String, String> h = new HashMap<>(super.getHeaders());
                h.putAll(Sesion.cabeceras(ProveedorActivity.this));
                return h;
            }
        };
        peticion.setRetryPolicy(new DefaultRetryPolicy(10000, 0, 1.0f));
        cola.add(peticion);
    }

    /** Adaptador mínimo que reutiliza la fila genérica de administración. */
    private class NotisAdapter extends RecyclerView.Adapter<NotisAdapter.Fila> {

        @NonNull
        @Override
        public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
            View v = LayoutInflater.from(padre.getContext())
                    .inflate(R.layout.fila_admin, padre, false);
            return new Fila(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Fila fila, int pos) {
            FirebaseServicio.Noti n = notis.get(pos);
            fila.titulo.setText(n.titulo);
            fila.sub.setText(n.detalle);
            fila.estado.setText(n.fecha);
            fila.estado.setTextColor(ContextCompat.getColor(ProveedorActivity.this,
                    R.color.verde_ok));
        }

        @Override
        public int getItemCount() {
            return notis.size();
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
}
