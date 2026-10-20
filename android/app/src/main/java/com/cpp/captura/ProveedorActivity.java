package com.cpp.captura;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ProveedorActivity — Sprint 10 «Sin servidor»: EL PROVEEDOR SUBE LAS CUENTAS.
 *
 * Modelo nuevo (aclarado por el usuario): el proveedor registra lo que el
 * cliente le debe → se guarda en Firestore (`ordenes`: clienteCorreo, monto,
 * fechaLimite) → el cliente lo ve AL INSTANTE en «💰 Cuentas por pagar»
 * (oyente en tiempo real, sin push de servidor) y lo paga adjuntando su
 * comprobante a Storage; aquí la lista se actualiza sola cuando eso pasa.
 *
 * Sin Laragon, sin IP: todo pasa por Firebase.
 */
public class ProveedorActivity extends AppCompatActivity {

    private static final int REQ_NOTIFICACIONES = 2001;

    private final List<QueryDocumentSnapshot> cuentas = new ArrayList<>();
    private CuentasAdapter adaptador;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_proveedor);

        ActionBar barraApp = getSupportActionBar();
        if (barraApp != null) {
            barraApp.setDisplayHomeAsUpEnabled(true);
            barraApp.setDisplayShowTitleEnabled(false);
        }

        String negocio = Sesion.negocio(this);
        TextView txtSub = findViewById(R.id.txtSubProveedor);
        txtSub.setText(getString(R.string.subtitulo_proveedor,
                negocio == null || negocio.isEmpty() ? Sesion.correo(this) : negocio));

        RecyclerView rec = findViewById(R.id.recNotificaciones);
        adaptador = new CuentasAdapter();
        rec.setLayoutManager(new LinearLayoutManager(this));
        rec.setAdapter(adaptador);

        TextView txtEstado = findViewById(R.id.txtEstadoProveedor);
        txtEstado.setText(R.string.proveedor_escuchando);

        findViewById(R.id.btnSubirCuenta).setOnClickListener(v -> dialogoNuevaCuenta());
        findViewById(R.id.btnSalirProveedor).setOnClickListener(v ->
                Sesion.salirAlLogin(ProveedorActivity.this));

        // Android 13+: pedir permiso para que los avisos del sistema se vean.
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICACIONES);
        }

        // Las cuentas de ESTE proveedor, EN TIEMPO REAL (se re-pintan solas
        // cuando el cliente paga). Sin orderBy (sin índice compuesto): Java ordena.
        FirebaseServicio.escucharOrdenesTodas(lista -> runOnUiThread(() -> {
            String uid = Sesion.uid(ProveedorActivity.this);
            cuentas.clear();
            for (QueryDocumentSnapshot d : lista) {
                if (uid.equals(d.getString("proveedorUid"))) {
                    cuentas.add(d);
                }
            }
            Collections.sort(cuentas, (a, b) -> Long.compare(creado(b), creado(a)));
            adaptador.notifyDataSetChanged();
            txtEstado.setText(cuentas.isEmpty()
                    ? getString(R.string.proveedor_sin_cuentas)
                    : getString(R.string.proveedor_cuentas, cuentas.size()));
        }));
    }

    private long creado(QueryDocumentSnapshot d) {
        Date c = d.getDate("creado");
        return c == null ? 0 : c.getTime();
    }

    // ========================================================================
    // ➕ Subir cuenta (lo que el cliente le debe)
    // ========================================================================

    private void dialogoNuevaCuenta() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_cuenta, null);
        EditText edtCliente = v.findViewById(R.id.edtCuentaCliente);
        EditText edtCodigo = v.findViewById(R.id.edtCuentaCodigo);
        EditText edtMonto = v.findViewById(R.id.edtCuentaMonto);
        EditText edtLimite = v.findViewById(R.id.edtCuentaLimite);

        new AlertDialog.Builder(this, R.style.CPPDialogo)
                .setTitle(R.string.cuenta_titulo)
                .setView(v)
                .setPositiveButton(R.string.reg_crear, (d, w) -> {
                    String cliente = edtCliente.getText().toString().trim();
                    String codigo = edtCodigo.getText().toString().trim();
                    String limite = edtLimite.getText().toString().trim();
                    double monto;
                    try {
                        monto = Double.parseDouble(edtMonto.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        monto = 0;
                    }
                    if (!cliente.contains("@") || codigo.length() < 2 || monto <= 0
                            || !limite.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        Toast.makeText(this, R.string.cuenta_faltan_datos,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    // S10.1: la cuenta SOLO va a un correo con cuenta real en
                    // la app (perfil usuarios/{uid}); un typo no queda perdido.
                    final double montoFinal = monto;   // lambda exige efectivamente final
                    Toast.makeText(this, R.string.cuenta_verificando, Toast.LENGTH_SHORT).show();
                    FirebaseServicio.existeCorreo(cliente, existe -> {
                        if (!existe) {
                            runOnUiThread(() -> Toast.makeText(this,
                                    R.string.cuenta_cliente_inexistente,
                                    Toast.LENGTH_LONG).show());
                            return;
                        }
                        guardarCuenta(cliente, codigo, montoFinal, limite);
                    }, e -> runOnUiThread(() -> Toast.makeText(this,
                            "Error al verificar el correo: " + e,
                            Toast.LENGTH_LONG).show()));
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    private void guardarCuenta(String cliente, String codigo, double monto, String limite) {
        Toast.makeText(this, R.string.cuenta_guardando, Toast.LENGTH_SHORT).show();

        Map<String, Object> orden = new HashMap<>();
        orden.put("codigo", codigo);
        orden.put("negocio", Sesion.negocio(this));
        orden.put("proveedorUid", Sesion.uid(this));
        orden.put("clienteCorreo", cliente);
        orden.put("monto", monto);
        orden.put("fechaEmision", hoy());
        orden.put("fechaLimite", limite);
        orden.put("estado", "pendiente");
        orden.put("creado", FieldValue.serverTimestamp());

        FirebaseServicio.guardarOrden(orden,
                id -> runOnUiThread(() -> Toast.makeText(this,
                        R.string.cuenta_exito, Toast.LENGTH_LONG).show()),
                e -> runOnUiThread(() -> Toast.makeText(this,
                        "Error al subir la cuenta: " + e, Toast.LENGTH_LONG).show()));
    }

    private String hoy() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    @Override
    public boolean onSupportNavigateUp() {
        Sesion.salirAlLogin(this);   // ⬅ al login (cierra la sesión)
        return true;
    }

    /** Una fila por cuenta: código · cliente · monto · estado. */
    private class CuentasAdapter extends RecyclerView.Adapter<CuentasAdapter.Fila> {

        @NonNull
        @Override
        public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
            return new Fila(LayoutInflater.from(padre.getContext())
                    .inflate(R.layout.fila_admin, padre, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Fila fila, int pos) {
            QueryDocumentSnapshot d = cuentas.get(pos);
            String cliente = texto(d, "clienteCorreo");
            fila.titulo.setText(texto(d, "codigo") + " · "
                    + (cliente.isEmpty() ? "?" : cliente));

            Double monto = d.getDouble("monto");
            String sub = texto(d, "negocio") + " · $"
                    + (monto == null ? "?" : String.format(Locale.US, "%.2f", monto));
            if (!texto(d, "fechaLimite").isEmpty()) {
                sub += " · vence " + texto(d, "fechaLimite");
            }
            fila.sub.setText(sub);

            boolean pagada = "pagada".equals(texto(d, "estado"));
            fila.estado.setText(pagada
                    ? getString(R.string.fila_cuenta_estado_pagada, texto(d, "pagadaEn"))
                    : getString(R.string.fila_cuenta_estado_pend));
            fila.estado.setTextColor(ContextCompat.getColor(ProveedorActivity.this,
                    pagada ? R.color.verde_ok : R.color.ambar_alerta));
        }

        @Override
        public int getItemCount() {
            return cuentas.size();
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

    private String texto(QueryDocumentSnapshot d, String clave) {
        String s = d.getString(clave);
        return s == null ? "" : s;
    }
}
