package com.cpp.captura;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PagosActivity — Sprint 8 "Cuentas por pagar" (RF-P1..P3).
 *
 * El apartado de DINERO del CLIENTE (capturista/admin/revisor; al proveedor
 * se le oculta el botón de entrada). Dos pestañas:
 *   ⏳ POR PAGAR — facturas Aprobadas sin pagar: tocar una → se ve su FOTO
 *      en grande → «💰 Marcar como pagada».
 *   ✅ PAGADAS — lo ya pagado, con CHIPS para revisarlas clasificadas
 *      (todas / aprobadas / no coinciden / discrepancia) y su foto.
 * Todo sale de GET /api/pagos.php; pagar/despagar es POST (capturista/admin;
 * la API respalda con 403 cualquier rol menor).
 */
public class PagosActivity extends AppCompatActivity {

    private static final String TAG = "CPP_S8_PAGOS";
    private static final int TIMEOUT_MS = 15000;

    private static final String MODO_POR_PAGAR = "por_pagar";
    private static final String MODO_PAGADAS = "pagadas";

    private RequestQueue cola;
    private String modo = MODO_POR_PAGAR;   // pestaña activa
    private String filtro = "todas";        // chip activo (solo en Pagadas)

    private final List<JSONObject> porPagar = new ArrayList<>();
    private final List<JSONObject> pagadas = new ArrayList<>();

    private AdaptadorPagos adaptador;
    private TextView txtEstado;
    private Button btnTabPorPagar;
    private Button btnTabPagadas;
    private View filaChips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pagos);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();   // el banner hace de título (criterio 0.7.1-ui)
        }
        cola = Volley.newRequestQueue(this);

        adaptador = new AdaptadorPagos();
        RecyclerView rec = findViewById(R.id.recPagos);
        rec.setLayoutManager(new LinearLayoutManager(this));
        rec.setAdapter(adaptador);

        txtEstado = findViewById(R.id.txtEstadoPagos);
        btnTabPorPagar = findViewById(R.id.btnTabPorPagar);
        btnTabPagadas = findViewById(R.id.btnTabPagadas);
        filaChips = findViewById(R.id.filaChipsPagos);

        btnTabPorPagar.setOnClickListener(v -> { modo = MODO_POR_PAGAR; pintar(); });
        btnTabPagadas.setOnClickListener(v -> { modo = MODO_PAGADAS; pintar(); });

        findViewById(R.id.btnChipTodas).setOnClickListener(v -> { filtro = "todas"; pintar(); });
        findViewById(R.id.btnChipAprobadas).setOnClickListener(v -> { filtro = "aprobadas"; pintar(); });
        findViewById(R.id.btnChipNoCoinciden).setOnClickListener(v -> { filtro = "no_coinciden"; pintar(); });
        findViewById(R.id.btnChipDiscrepancias).setOnClickListener(v -> { filtro = "discrepancias"; pintar(); });

        findViewById(R.id.btnVolverPagos).setOnClickListener(v -> finish());

        cargar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (cola != null) {
            cola.cancelAll(TAG);
        }
    }

    private String urlBase() {
        return "http://" + Sesion.servidor(this) + "/cpp/api";
    }

    /** Cabeceras con el token (copia mutable: patrón del hotfix 0.8.2). */
    private Map<String, String> cabeceras() {
        Map<String, String> h = new HashMap<>();
        h.putAll(Sesion.cabeceras(this));
        return h;
    }

    // ========================================================================
    // Red — GET /api/pagos.php
    // ========================================================================
    private void cargar() {
        txtEstado.setText(R.string.pagos_cargando);
        JsonObjectRequest peticion = new JsonObjectRequest(Request.Method.GET,
                urlBase() + "/pagos.php", null,
                response -> {
                    JSONObject datos = response.optJSONObject("datos");
                    JSONArray a = datos != null ? datos.optJSONArray("por_pagar") : null;
                    JSONArray b = datos != null ? datos.optJSONArray("pagadas") : null;
                    porPagar.clear();
                    pagadas.clear();
                    if (a != null) {
                        for (int i = 0; i < a.length(); i++) porPagar.add(a.optJSONObject(i));
                    }
                    if (b != null) {
                        for (int i = 0; i < b.length(); i++) pagadas.add(b.optJSONObject(i));
                    }
                    pintar();
                },
                error -> {
                    if (Sesion.sinSesion(error)) {
                        Sesion.salirAlLogin(PagosActivity.this);
                        return;
                    }
                    txtEstado.setText(getString(R.string.pagos_error, mensajeVolley(error)));
                }) {
            @Override
            public Map<String, String> getHeaders() {
                return cabeceras();
            }
        };
        peticion.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1.0f));
        peticion.setTag(TAG);
        cola.add(peticion);
    }

    /** Mensaje de error del servidor (el JSON trae "mensaje" claro). */
    private String mensajeVolley(VolleyError error) {
        if (error.networkResponse != null && error.networkResponse.data != null) {
            try {
                JSONObject j = new JSONObject(new String(error.networkResponse.data));
                return j.optString("mensaje", error.getMessage());
            } catch (Exception e) {
                // sin JSON en el cuerpo
            }
        }
        return error.getMessage() == null ? "sin conexión" : error.getMessage();
    }

    // ========================================================================
    // Pintado — pestañas, chips, lista y vacíos
    // ========================================================================
    private void pintar() {
        boolean enPorPagar = MODO_POR_PAGAR.equals(modo);
        filaChips.setVisibility(enPorPagar ? View.GONE : View.VISIBLE);

        btnTabPorPagar.setText(getString(R.string.tab_por_pagar, porPagar.size()));
        btnTabPagadas.setText(getString(R.string.tab_pagadas, pagadas.size()));
        btnTabPorPagar.setTextColor(ContextCompat.getColor(this,
                enPorPagar ? R.color.primario : R.color.hint_sobre_tarjeta));
        btnTabPagadas.setTextColor(ContextCompat.getColor(this,
                enPorPagar ? R.color.hint_sobre_tarjeta : R.color.primario));

        List<JSONObject> lista = listaVisible();
        adaptador.setLista(lista);
        adaptador.notifyDataSetChanged();

        if (lista.isEmpty()) {
            if (!enPorPagar && !"todas".equals(filtro)) {
                txtEstado.setText(R.string.pagos_vacio_filtro);
            } else if (enPorPagar) {
                txtEstado.setText(R.string.pagos_vacio_por_pagar);
            } else {
                txtEstado.setText(R.string.pagos_vacio_pagadas);
            }
        } else {
            txtEstado.setText("");
        }
    }

    private List<JSONObject> listaVisible() {
        if (MODO_POR_PAGAR.equals(modo)) {
            return porPagar;
        }
        if ("todas".equals(filtro)) {
            return pagadas;
        }
        List<JSONObject> r = new ArrayList<>();
        for (JSONObject f : pagadas) {
            if (filtro.equals(clase(f))) {
                r.add(f);
            }
        }
        return r;
    }

    /** Clasificación RF-P3: aprobadas / no_coinciden / discrepancias / otras. */
    private String clase(JSONObject f) {
        String estado = f.optString("estado", "");
        if ("Aprobada".equals(estado)) {
            return "aprobadas";
        }
        if ("Con Discrepancia".equals(estado)) {
            return "PROVEEDOR_NO_COINCIDE".equals(f.optString("motivo_discrepancia", ""))
                    ? "no_coinciden" : "discrepancias";
        }
        return "otras";
    }

    // ========================================================================
    // Diálogo con la FOTO de la factura + pagar/despagar (RF-P2/P3)
    // ========================================================================
    private void dialogFactura(final JSONObject f) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_pago, null);
        ImageView img = v.findViewById(R.id.imgFotoPago);
        TextView txt = v.findViewById(R.id.txtInfoPago);

        int id = f.optInt("id", 0);
        String proveedor = texto(f, "proveedor_detectado");
        if (proveedor.isEmpty()) {
            proveedor = texto(f, "orden_proveedor");
        }
        StringBuilder info = new StringBuilder("🧾 Factura #").append(id)
                .append(" · ").append(texto(f, "orden_codigo")).append('\n')
                .append(proveedor.isEmpty() ? "?" : proveedor);
        if (!texto(f, "monto_facturado").isEmpty()) {
            info.append(" · $").append(texto(f, "monto_facturado"));
        }
        if (!texto(f, "folio").isEmpty()) {
            info.append(" · ").append(texto(f, "folio"));
        }
        if (!texto(f, "pagada_en").isEmpty()) {
            info.append('\n').append("💰 Pagada: ").append(texto(f, "pagada_en"));
        }
        txt.setText(info.toString());

        String ruta = texto(f, "ruta_imagen");
        if (!ruta.isEmpty()) {
            ImageRequest foto = new ImageRequest(
                    "http://" + Sesion.servidor(this) + "/cpp/" + ruta,
                    bitmap -> img.setImageBitmap(bitmap),
                    0, 0, ImageView.ScaleType.FIT_CENTER, Bitmap.Config.RGB_565,
                    error -> Toast.makeText(this,
                            "No se pudo cargar la foto", Toast.LENGTH_SHORT).show());
            foto.setTag(TAG);
            cola.add(foto);
        }

        boolean enPorPagar = MODO_POR_PAGAR.equals(modo);
        new AlertDialog.Builder(this)
                .setTitle("🧾 Factura #" + id)
                .setView(v)
                .setPositiveButton(enPorPagar ? R.string.pago_marcar : R.string.pago_desmarcar,
                        (d, w) -> accionPago(f, enPorPagar ? "pagar" : "despagar"))
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    /** optString saneado: null/“null” → vacío. */
    private String texto(JSONObject f, String clave) {
        String s = f.optString(clave, "");
        return (s == null || "null".equals(s)) ? "" : s;
    }

    // ========================================================================
    // POST /api/pagos.php — pagar / despagar (RF-P2)
    // ========================================================================
    private void accionPago(JSONObject f, String accion) {
        JSONObject cuerpo = new JSONObject();
        try {
            cuerpo.put("accion", accion);
            cuerpo.put("id", f.optInt("id", 0));
        } catch (Exception e) {
            Toast.makeText(this, "Error interno: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        JsonObjectRequest peticion = new JsonObjectRequest(Request.Method.POST,
                urlBase() + "/pagos.php", cuerpo,
                response -> {
                    Toast.makeText(this, response.optString("mensaje", "OK"),
                            Toast.LENGTH_LONG).show();
                    cargar();   // refresca las dos pestañas
                },
                error -> {
                    if (Sesion.sinSesion(error)) {
                        Sesion.salirAlLogin(PagosActivity.this);
                        return;
                    }
                    Toast.makeText(this, mensajeVolley(error), Toast.LENGTH_LONG).show();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                return cabeceras();
            }
        };
        peticion.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1.0f));
        peticion.setTag(TAG);
        cola.add(peticion);
    }

    // ========================================================================
    // Adaptador — reutiliza la fila genérica de administración
    // ========================================================================
    private class AdaptadorPagos extends RecyclerView.Adapter<AdaptadorPagos.Fila> {

        private final List<JSONObject> lista = new ArrayList<>();

        void setLista(List<JSONObject> nueva) {
            lista.clear();
            lista.addAll(nueva);
        }

        @NonNull
        @Override
        public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
            return new Fila(LayoutInflater.from(padre.getContext())
                    .inflate(R.layout.fila_admin, padre, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Fila fila, int pos) {
            final JSONObject f = lista.get(pos);
            fila.titulo.setText("🧾 Factura #" + f.optInt("id", 0)
                    + " · " + texto(f, "orden_codigo"));

            String proveedor = texto(f, "proveedor_detectado");
            if (proveedor.isEmpty()) {
                proveedor = texto(f, "orden_proveedor");
            }
            String sub = proveedor.isEmpty() ? "?" : proveedor;
            if (!texto(f, "monto_facturado").isEmpty()) {
                sub += " · $" + texto(f, "monto_facturado");
            }
            fila.sub.setText(sub);

            String textoEstado;
            int color;
            if (MODO_POR_PAGAR.equals(modo)) {
                textoEstado = "✅ Aprobada — toca para ver y pagar";
                color = R.color.verde_ok;
            } else if ("aprobadas".equals(clase(f))) {
                textoEstado = "✅ Aprobada · 💰 pagada";
                color = R.color.verde_ok;
            } else if ("no_coinciden".equals(clase(f))) {
                textoEstado = "❌ Proveedor no coincide · 💰 pagada";
                color = R.color.rojo_error;
            } else {
                textoEstado = "⚠️ " + texto(f, "motivo_discrepancia") + " · 💰 pagada";
                color = R.color.ambar_alerta;
            }
            fila.estado.setText(textoEstado);
            fila.estado.setTextColor(ContextCompat.getColor(PagosActivity.this, color));

            fila.itemView.setOnClickListener(v -> dialogFactura(f));
        }

        @Override
        public int getItemCount() {
            return lista.size();
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
