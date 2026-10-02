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
import java.util.List;

/**
 * ListadoActivity — Sprint 4 "Integración y Demo" (RF-M5 + RF-M6 simplificado).
 *
 * Muestra TODAS las facturas del servidor (GET /api/facturas.php, que ya las
 * devuelve de la más reciente a la más antigua) con su badge de estado:
 *
 *     ✅ Aprobada   ·   ⚠️ Con Discrepancia   ·   ⏳ Pendiente
 *
 * ...junto con el proveedor detectado por el OCR, folio, monto, % de riesgo
 * y —si la hay— la causa de la discrepancia. Es la pantalla que valida la
 * hipótesis H4 del MVP: el usuario entiende el resultado sin explicación.
 *
 * El servidor se recibe por Intent extra ("servidor") desde MainActivity;
 * la IP es la misma que ya tecleó el usuario para enviar fotos.
 */
public class ListadoActivity extends AppCompatActivity {

    private static final String TAG = "CPP_S4_LISTA";
    private static final int TIMEOUT_MS = 15000;

    private RequestQueue cola;
    private RecyclerView reciclador;
    private ProgressBar barra;
    private TextView txtEstado;      // "cargando…" / "sin facturas" / errores
    private FacturasAdapter adaptador;
    private String servidor;
    private TextView chipOk;        // 0.6.0-ui: resumen por estado (✅/⚠️/⏳)
    private TextView chipAlerta;
    private TextView chipPend;

    private final List<Factura> facturas = new ArrayList<>();

    /** Modelo de una fila (GET /api/facturas.php → datos.facturas[]). */
    private static class Factura {
        final int id;
        final String orden;      // p. ej. "OC-001"
        final String folio;      // "F-1001" o "—"
        final String proveedor;  // detectado por OCR o, si no, el de la orden
        final String monto;      // "4,850.00" o "—"
        final String riesgo;     // "27.3" o null (aún sin procesar)
        final String estado;     // Pendiente | Aprobada | Con Discrepancia
        final String motivo;     // MONTO_EXCEDE_ORDEN… o null

        Factura(JSONObject f) {
            this.id = f.optInt("id", 0);
            this.orden = f.optString("orden_codigo", "?");
            String folioCrudo = f.optString("folio", "");
            this.folio = folioCrudo.isEmpty() || "null".equals(folioCrudo) ? "—" : folioCrudo;
            String prov = f.optString("proveedor_detectado", "");
            if (prov.isEmpty() || "null".equals(prov)) {
                prov = f.optString("orden_proveedor", "?");
            }
            this.proveedor = prov;
            String m = f.optString("monto_facturado", "");
            this.monto = m.isEmpty() || "null".equals(m) ? "—" : "$" + m;
            String r = f.optString("riesgo", "");
            this.riesgo = r.isEmpty() || "null".equals(r) ? null : r;
            this.estado = f.optString("estado", "?");
            String mo = f.optString("motivo_discrepancia", "");
            this.motivo = mo.isEmpty() || "null".equals(mo) ? null : mo;
        }
    }

    // ========================================================================
    // Ciclo de vida
    // ========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listado);

        servidor = getIntent().getStringExtra("servidor");
        if (servidor == null || servidor.trim().isEmpty()) {
            servidor = "10.0.2.2";     // mismo default que MainActivity
        }

        ActionBar barraApp = getSupportActionBar();
        if (barraApp != null) {
            barraApp.setDisplayHomeAsUpEnabled(true);     // ⬅ volver a captura
            barraApp.setDisplayShowTitleEnabled(false);   // 0.7.1-ui: sin «Cuentas», ya lo dice el banner
        }

        cola = Volley.newRequestQueue(this);
        reciclador = findViewById(R.id.recFacturas);
        barra = findViewById(R.id.barraLista);
        txtEstado = findViewById(R.id.txtEstadoLista);
        Button btnActualizar = findViewById(R.id.btnActualizar);
        chipOk = findViewById(R.id.chipOk);
        chipAlerta = findViewById(R.id.chipAlerta);
        chipPend = findViewById(R.id.chipPend);

        adaptador = new FacturasAdapter();
        reciclador.setLayoutManager(new LinearLayoutManager(this));
        reciclador.setAdapter(adaptador);

        btnActualizar.setOnClickListener(v -> cargar());
        cargar();
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
        finish();     // botón ⬅ de la ActionBar → regresar a MainActivity
        return true;
    }

    // ========================================================================
    // Red — GET /api/facturas.php (RF-M5)
    // ========================================================================

    private String urlBase() {
        return "http://" + servidor + "/cpp/api";
    }

    /** Descarga el listado y repinta las tarjetas. */
    private void cargar() {
        barra.setVisibility(View.VISIBLE);
        txtEstado.setVisibility(View.VISIBLE);
        txtEstado.setTextColor(ContextCompat.getColor(this, R.color.texto_normal));
        txtEstado.setText(getString(R.string.cargando_listado, urlBase() + "/facturas.php"));

        JsonObjectRequest peticion = new JsonObjectRequest(Request.Method.GET,
                urlBase() + "/facturas.php", null,
                response -> {
                    barra.setVisibility(View.GONE);
                    facturas.clear();
                    JSONObject datos = response.optJSONObject("datos");
                    JSONArray lista = datos != null ? datos.optJSONArray("facturas") : null;
                    if (lista != null) {
                        for (int i = 0; i < lista.length(); i++) {
                            JSONObject f = lista.optJSONObject(i);
                            if (f != null) {
                                facturas.add(new Factura(f));
                            }
                        }
                    }
                    adaptador.notifyDataSetChanged();
                    pintarResumen();
                    txtEstado.setVisibility(facturas.isEmpty() ? View.VISIBLE : View.GONE);
                    txtEstado.setText(R.string.sin_facturas);
                },
                error -> {
                    barra.setVisibility(View.GONE);
                    facturas.clear();
                    adaptador.notifyDataSetChanged();
                    pintarResumen();   // sin datos → los tres chips se ocultan
                    txtEstado.setVisibility(View.VISIBLE);
                    txtEstado.setTextColor(ContextCompat.getColor(this, R.color.rojo_error));
                    txtEstado.setText(getString(R.string.error_listado, mensajeVolley(error)));
                });

        peticion.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1.0f));
        peticion.setTag(TAG);
        cola.add(peticion);
    }

    /**
     * 0.6.0-ui: resumen superior por estado — una píldora ✅/⚠️/⏳ por cada estado
     * con conteo mayor que cero (tinte pastel del color del estado). Da el estado
     * global del día de un vistazo, antes de leer las tarjetas.
     */
    private void pintarResumen() {
        int ok = 0, alerta = 0, pendientes = 0;
        for (Factura f : facturas) {
            switch (f.estado) {
                case "Aprobada":          ok++;         break;
                case "Con Discrepancia":  alerta++;     break;
                default:                  pendientes++; break;
            }
        }
        pintarChip(chipOk, R.string.chip_ok, ok, R.color.verde_ok);
        pintarChip(chipAlerta, R.string.chip_alerta, alerta, R.color.ambar_alerta);
        pintarChip(chipPend, R.string.chip_pend, pendientes, R.color.gris_pendiente);
    }

    /** Pinta (u oculta si n=0) una píldora del resumen con su color de estado. */
    private void pintarChip(TextView chip, int textoRes, int n, int colorRes) {
        chip.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        if (n > 0) {
            int color = ContextCompat.getColor(this, colorRes);
            chip.setText(getString(textoRes, n));
            chip.setTextColor(color);
            chip.setBackgroundTintList(ColorStateList.valueOf(
                    (color & 0x00FFFFFF) | 0x33000000));
        }
    }

    /**
     * 0.7.2-ui: convierte el motivo de la discrepancia en un CONSEJO accionable
     * (qué puede hacer el usuario). La hipótesis H4 pide que el resultado se
     * entienda sin explicación; con esto también se sabe cómo resolverlo.
     */
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
        } else {
            res = R.string.ayuda_generica;
        }
        return getString(res);
    }

    /** Igual que en MainActivity: extrae el «mensaje» del JSON de error de la API. */
    private String mensajeVolley(VolleyError error) {
        NetworkResponse resp = error.networkResponse;
        if (resp != null && resp.data != null) {
            try {
                JSONObject json = new JSONObject(new String(resp.data, "UTF-8"));
                String detalle = json.optString("mensaje", "");
                if (!detalle.isEmpty()) {
                    return "HTTP " + resp.statusCode + ": " + detalle;
                }
            } catch (Exception ignored) {
                // cuerpo sin JSON legible
            }
            return "HTTP " + resp.statusCode;
        }
        return error.getMessage() == null ? "sin detalles" : error.getMessage();
    }

    // ========================================================================
    // Adapter — una tarjeta por factura con badge (RF-M6 simplificado, sin filtros)
    // ========================================================================
    private class FacturasAdapter extends RecyclerView.Adapter<FacturasAdapter.Fila> {

        @NonNull
        @Override
        public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
            View v = LayoutInflater.from(padre.getContext())
                    .inflate(R.layout.fila_factura, padre, false);
            return new Fila(v);
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
                case "Con Discrepancia":
                    badge = "⚠️";
                    color = ContextCompat.getColor(ListadoActivity.this, R.color.ambar_alerta);
                    break;
                default:                                    // «Pendiente»
                    badge = "⏳";
                    color = ContextCompat.getColor(ListadoActivity.this, R.color.gris_pendiente);
                    break;
            }

            fila.badge.setText(badge);
            fila.badge.setBackgroundTintList(ColorStateList.valueOf(
                    (color & 0x00FFFFFF) | 0x33000000));  // 0.6.0-ui: círculo pastel
            fila.titulo.setText(getString(R.string.fila_titulo, f.id, f.orden, f.folio));
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
                fila.ayuda.setVisibility(View.VISIBLE);          // 0.7.2-ui: consejo
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

        /** Referencias a las vistas de una fila (patrón ViewHolder). */
        class Fila extends RecyclerView.ViewHolder {
            final TextView badge;
            final TextView titulo;
            final TextView monto;
            final TextView proveedor;
            final TextView estado;
            final TextView motivo;
            final TextView ayuda;      // 0.7.2-ui: qué hacer ante la discrepancia

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
