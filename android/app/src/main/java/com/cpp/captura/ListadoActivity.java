package com.cpp.captura;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ListadoActivity — Sprint 10: las facturas viven en FIRESTORE.
 *
 * Cada factura fue conciliada EN EL TELÉFONO (ML Kit + Java, ver Motores) y
 * guardada en la colección `facturas`; aquí se listan EN VIVO (oyente) con
 * sus badges ✅/⚠️, motivo, consejo 💡 y los CHIPS FILTRABLES del S9 (RF-M6).
 * El cliente ve las suyas (subidoPor); el admin ve todas.
 */
public class ListadoActivity extends AppCompatActivity {

    private RecyclerView reciclador;
    private ProgressBar barra;
    private TextView txtEstado;
    private FacturasAdapter adaptador;
    private TextView chipOk;
    private TextView chipAlerta;
    private TextView chipPend;

    /** S9 (RF-M6): filtro activo (null = todas). */
    private String filtroEstado;

    private final List<Factura> todas = new ArrayList<>();
    private final List<Factura> facturas = new ArrayList<>();

    /** Modelo de una fila (documento de Firestore `facturas`). */
    private static class Factura {
        final int n;
        final String folio;
        final String proveedor;
        final String monto;
        final String riesgo;
        final String estado;
        final String motivo;

        Factura(QueryDocumentSnapshot d, int n) {
            this.n = n;
            String f = d.getString("folio");
            this.folio = f == null || f.isEmpty() ? "—" : f;
            String p = d.getString("proveedor");
            this.proveedor = p == null || p.isEmpty() ? "?" : p;
            Double m = d.getDouble("monto");
            this.monto = m == null ? "—" : "$" + String.format(Locale.US, "%.2f", m);
            Double r = d.getDouble("riesgo");
            this.riesgo = r == null ? null : String.format(Locale.US, "%.1f", r);
            this.estado = "APROBADA".equals(d.getString("veredicto"))
                    ? "Aprobada" : "Con Discrepancia";
            String mo = d.getString("motivo");
            this.motivo = mo == null || mo.isEmpty() ? null : mo;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listado);

        ActionBar barraApp = getSupportActionBar();
        if (barraApp != null) {
            barraApp.setDisplayHomeAsUpEnabled(true);
            barraApp.setDisplayShowTitleEnabled(false);
        }

        reciclador = findViewById(R.id.recFacturas);
        barra = findViewById(R.id.barraLista);
        txtEstado = findViewById(R.id.txtEstadoLista);
        chipOk = findViewById(R.id.chipOk);
        chipAlerta = findViewById(R.id.chipAlerta);
        chipPend = findViewById(R.id.chipPend);

        // S10: el listado es EN VIVO — el botón de actualizar sobra.
        Button btnActualizar = findViewById(R.id.btnActualizar);
        btnActualizar.setVisibility(View.GONE);

        adaptador = new FacturasAdapter();
        reciclador.setLayoutManager(new LinearLayoutManager(this));
        reciclador.setAdapter(adaptador);

        // S9 (RF-M6): los chips filtran (tap = filtrar, tap de nuevo = quitar).
        chipOk.setOnClickListener(v -> alternarFiltro("Aprobada"));
        chipAlerta.setOnClickListener(v -> alternarFiltro("Con Discrepancia"));
        chipPend.setOnClickListener(v -> alternarFiltro("Pendiente"));

        barra.setVisibility(View.VISIBLE);
        txtEstado.setVisibility(View.VISIBLE);
        txtEstado.setTextColor(ContextCompat.getColor(this, R.color.texto_normal));
        txtEstado.setText(R.string.pagos_cargando);

        FirebaseServicio.escucharFacturas(Sesion.correo(this), Sesion.esAdmin(this),
                lista -> runOnUiThread(() -> {
                    barra.setVisibility(View.GONE);
                    todas.clear();
                    Collections.sort(lista, Comparator.comparing(
                            (QueryDocumentSnapshot d) -> creadoDe(d)).reversed());
                    int n = 1;
                    for (QueryDocumentSnapshot d : lista) {
                        todas.add(new Factura(d, n++));
                    }
                    aplicarFiltro();
                }));
    }

    private long creadoDe(QueryDocumentSnapshot d) {
        Date c = d.getDate("creado");
        return c == null ? 0 : c.getTime();
    }

    /** S9 (RF-M6): aplica el filtro por estado y repinta todo. */
    private void aplicarFiltro() {
        facturas.clear();
        for (Factura f : todas) {
            if (filtroEstado == null || filtroEstado.equals(f.estado)) {
                facturas.add(f);
            }
        }
        adaptador.notifyDataSetChanged();
        pintarResumen();
        txtEstado.setTextColor(ContextCompat.getColor(this, R.color.texto_normal));
        if (todas.isEmpty()) {
            txtEstado.setVisibility(View.VISIBLE);
            txtEstado.setText(R.string.sin_facturas);
        } else if (filtroEstado != null) {
            txtEstado.setVisibility(View.VISIBLE);
            txtEstado.setText(getString(R.string.filtro_activa, filtroEstado,
                    facturas.size(), todas.size()));
        } else {
            txtEstado.setVisibility(View.GONE);
        }
    }

    private void alternarFiltro(String estado) {
        filtroEstado = estado.equals(filtroEstado) ? null : estado;
        aplicarFiltro();
    }

    /** Resumen ✅/⚠️ (del total; el chip del filtro activo va sólido). */
    private void pintarResumen() {
        int ok = 0, alerta = 0;
        for (Factura f : todas) {
            if ("Aprobada".equals(f.estado)) {
                ok++;
            } else {
                alerta++;
            }
        }
        pintarChip(chipOk, R.string.chip_ok, ok, R.color.verde_ok,
                "Aprobada".equals(filtroEstado));
        pintarChip(chipAlerta, R.string.chip_alerta, alerta, R.color.ambar_alerta,
                "Con Discrepancia".equals(filtroEstado));
        chipPend.setVisibility(View.GONE);   // S10: ya no hay «Pendientes»
    }

    private void pintarChip(TextView chip, int textoRes, int n, int colorRes, boolean activo) {
        chip.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        if (n > 0) {
            int color = ContextCompat.getColor(this, colorRes);
            chip.setText(getString(textoRes, n));
            chip.setTextColor(activo ? 0xFFFFFFFF : color);
            chip.setBackgroundTintList(ColorStateList.valueOf(activo
                    ? (0xFF000000 | color)
                    : (color & 0x00FFFFFF) | 0x33000000));
        }
    }

    /** 0.7.2-ui: qué HACER ante cada discrepancia (más la del S9: duplicados). */
    private String textoAyuda(String motivo) {
        int res;
        if (motivo.contains("MONTO_EXCEDE")) {
            res = R.string.ayuda_monto_excede;
        } else if (motivo.contains("RIESGO")) {
            res = R.string.ayuda_riesgo_alto;
        } else if (motivo.contains("PROVEEDOR")) {
            res = R.string.ayuda_proveedor;
        } else if (motivo.contains("FECHA")) {
            res = R.string.ayuda_fecha;
        } else if (motivo.contains("DUPLICADA")) {
            res = R.string.ayuda_duplicada;
        } else {
            res = R.string.ayuda_generica;
        }
        return getString(res);
    }

    /** Adaptador — una tarjeta por factura con badge. */
    private class FacturasAdapter extends RecyclerView.Adapter<FacturasAdapter.Fila> {

        @NonNull
        @Override
        public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
            return new Fila(LayoutInflater.from(padre.getContext())
                    .inflate(R.layout.fila_factura, padre, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Fila fila, int pos) {
            Factura f = facturas.get(pos);

            String badge;
            int color;
            switch (f.estado) {
                case "Aprobada":
                    badge = "✅";
                    color = ContextCompat.getColor(ListadoActivity.this, R.color.verde_ok);
                    break;
                default:
                    badge = "⚠️";
                    color = ContextCompat.getColor(ListadoActivity.this, R.color.ambar_alerta);
                    break;
            }

            fila.badge.setText(badge);
            fila.badge.setBackgroundTintList(ColorStateList.valueOf(
                    (color & 0x00FFFFFF) | 0x33000000));
            fila.titulo.setText(getString(R.string.fila_titulo, f.n, "—", f.folio));
            fila.monto.setText(f.monto);
            fila.monto.setTextColor(color);
            fila.proveedor.setText(f.proveedor);
            fila.estado.setText(f.riesgo != null
                    ? getString(R.string.fila_estado, f.estado, f.riesgo)
                    : f.estado);
            fila.estado.setTextColor(color);
            if (f.motivo != null) {
                fila.motivo.setVisibility(View.VISIBLE);
                fila.motivo.setText(getString(R.string.fila_motivo, f.motivo));
                fila.motivo.setTextColor(ContextCompat.getColor(
                        ListadoActivity.this, R.color.rojo_error));
                fila.ayuda.setVisibility(View.VISIBLE);
                fila.ayuda.setText(textoAyuda(f.motivo));
            } else {
                fila.motivo.setVisibility(View.GONE);
                fila.ayuda.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return facturas.size();
        }

        class Fila extends RecyclerView.ViewHolder {
            final TextView badge;
            final TextView titulo;
            final TextView monto;
            final TextView proveedor;
            final TextView estado;
            final TextView motivo;
            final TextView ayuda;

            Fila(View v) {
                super(v);
                badge = v.findViewById(R.id.txtBadge);
                titulo = v.findViewById(R.id.txtFilaTitulo);
                monto = v.findViewById(R.id.txtFilaMonto);
                proveedor = v.findViewById(R.id.txtFilaProveedor);
                estado = v.findViewById(R.id.txtFilaEstado);
                motivo = v.findViewById(R.id.txtFilaMotivo);
                ayuda = v.findViewById(R.id.txtFilaAyuda);
            }
        }
    }
}
