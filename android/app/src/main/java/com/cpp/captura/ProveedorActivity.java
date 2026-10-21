package com.cpp.captura;

import android.Manifest;
import android.content.Intent;
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

        // S10.5: candado obligatorio — sin verificar huella/contraseña no se entra.
        if (Candado.requiereCandado(this)) {
            startActivity(new Intent(this, CandadoActivity.class));
            finish();
            return;
        }

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

    // ========================================================================
    // S10.6: ficha de una cuenta — el PROVEEDOR decide sobre las vencidas
    // ========================================================================

    private int diasDe(String fechaLimite) {
        long jl = Motores.juliano(fechaLimite);
        long jh = Motores.juliano(hoy());
        if (jl < 0 || jh < 0) {
            return Integer.MAX_VALUE;
        }
        return (int) (jl - jh);
    }

    /** Vencida, no pagada y no reabierta: el pago del cliente está CERRADO. */
    private boolean esCerrada(QueryDocumentSnapshot d) {
        int dias = diasDe(texto(d, "fechaLimite"));
        return !"pagada".equals(texto(d, "estado"))
                && dias != Integer.MAX_VALUE && dias < 0
                && !Boolean.TRUE.equals(d.getBoolean("reabierta"));
    }

    private void dialogoCuenta(QueryDocumentSnapshot d) {
        Double monto = d.getDouble("monto");
        Double interes = d.getDouble("interes");
        StringBuilder info = new StringBuilder()
                .append(texto(d, "codigo")).append(" · ")
                .append(texto(d, "negocio")).append('\n')
                .append(texto(d, "clienteCorreo"))
                .append(" · $").append(monto == null ? "?"
                        : String.format(Locale.US, "%.2f", monto)).append('\n');
        if (interes != null && interes > 0) {
            info.append(getString(R.string.incluye_interes,
                    String.format(Locale.US, "%.2f", interes))).append('\n');
        }
        info.append("Vence: ").append(texto(d, "fechaLimite"));
        if ("pagada".equals(texto(d, "estado"))) {
            info.append('\n').append(getString(R.string.fila_cuenta_estado_pagada,
                    texto(d, "pagadaEn")));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CPPDialogo)
                .setTitle(R.string.pagos_cuenta_titulo)
                .setMessage(info.toString())
                .setNeutralButton(R.string.cancelar, null);

        // S10.6: si está CERRADA (vencida sin reabrir), el proveedor decide.
        if (esCerrada(d)) {
            builder.setMessage(info + "\n\n"
                            + getString(R.string.prov_info_cerrada, texto(d, "clienteCorreo")))
                    .setPositiveButton(R.string.prov_agregar_interes,
                            (dlg, w) -> dialogoInteres(d))
                    .setNegativeButton(R.string.prov_reabrir,
                            (dlg, w) -> reabrir(d));
        }
        builder.show();
    }

    /** Reabre el pago tal cual (sin interés): el cliente vuelve a poder pagar. */
    private void reabrir(QueryDocumentSnapshot d) {
        Map<String, Object> cambios = new HashMap<>();
        cambios.put("reabierta", true);
        d.getReference().update(cambios)
                .addOnSuccessListener(v -> Toast.makeText(this,
                        getString(R.string.prov_reabierta_ok, texto(d, "clienteCorreo")),
                        Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Error al reabrir: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    /** Agrega el interés acordado al monto y REABRE el pago. */
    private void dialogoInteres(QueryDocumentSnapshot d) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_interes, null);
        EditText edtInteres = v.findViewById(R.id.edtInteres);

        new AlertDialog.Builder(this, R.style.CPPDialogo)
                .setTitle(R.string.interes_titulo)
                .setView(v)
                .setPositiveButton(R.string.interes_agregar, (dlg, w) -> {
                    double interes;
                    try {
                        interes = Double.parseDouble(edtInteres.getText()
                                .toString().trim().replace(',', '.'));
                    } catch (NumberFormatException e) {
                        interes = 0;
                    }
                    if (interes <= 0) {
                        Toast.makeText(this, R.string.interes_monto_mal,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    double montoActual = d.getDouble("monto") == null
                            ? 0 : d.getDouble("monto");
                    double interesPrevio = d.getDouble("interes") == null
                            ? 0 : d.getDouble("interes");
                    Map<String, Object> cambios = new HashMap<>();
                    cambios.put("monto", montoActual + interes);
                    cambios.put("interes", interesPrevio + interes);
                    cambios.put("reabierta", true);
                    d.getReference().update(cambios)
                            .addOnSuccessListener(x -> Toast.makeText(this,
                                    getString(R.string.prov_interes_ok,
                                            String.format(Locale.US, "%.2f",
                                                    montoActual + interes)),
                                    Toast.LENGTH_LONG).show())
                            .addOnFailureListener(e -> Toast.makeText(this,
                                    "Error al agregar el interés: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
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
            boolean reabierta = Boolean.TRUE.equals(d.getBoolean("reabierta"));
            if (pagada) {
                fila.estado.setText(getString(R.string.fila_cuenta_estado_pagada,
                        texto(d, "pagadaEn")));
                fila.estado.setTextColor(ContextCompat.getColor(
                        ProveedorActivity.this, R.color.verde_ok));
            } else if (esCerrada(d)) {          // S10.6: tú decides
                fila.estado.setText(R.string.prov_fila_cerrada);
                fila.estado.setTextColor(ContextCompat.getColor(
                        ProveedorActivity.this, R.color.rojo_error));
            } else if (reabierta) {             // S10.6: reabierta tras acuerdo
                fila.estado.setText(R.string.fila_reabierta);
                fila.estado.setTextColor(ContextCompat.getColor(
                        ProveedorActivity.this, R.color.verde_ok));
            } else {
                fila.estado.setText(R.string.fila_cuenta_estado_pend);
                fila.estado.setTextColor(ContextCompat.getColor(
                        ProveedorActivity.this, R.color.ambar_alerta));
            }

            // S10.6: tocar la cuenta abre su ficha (decisión si está cerrada).
            fila.itemView.setOnClickListener(v -> dialogoCuenta(d));
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
