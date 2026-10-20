package com.cpp.captura;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PagosActivity — Sprint 10: las cuentas que el PROVEEDOR subió (Firestore).
 *
 * ⏳ POR PAGAR: cuentas con estado "pendiente" dirigidas al correo de este
 *    cliente (el admin ve todas). El vencimiento se calcula EN el teléfono
 *    con la fechaLimite que puso el proveedor (días julianos de Motores):
 *    rojo si quedan ≤3 días o ya venció (RF-M7).
 * ✅ PAGADAS: con el COMPROBANTE que subió el cliente a Firebase Storage.
 *
 * Pagar (S8.3/S9.1) sigue igual de estricto: SIN la foto del comprobante
 * no hay paso a pagadas — el diálogo de pendientes no muestra ninguna foto.
 */
public class PagosActivity extends AppCompatActivity {

    private static final String MODO_POR_PAGAR = "por_pagar";
    private static final String MODO_PAGADAS = "pagadas";
    private static final int REQ_COMPROBANTE = 2001;
    private static final int MAX_LADO_COMPROBANTE = 1600;

    private String modo = MODO_POR_PAGAR;

    private final List<QueryDocumentSnapshot> porPagar = new ArrayList<>();
    private final List<QueryDocumentSnapshot> pagadas = new ArrayList<>();

    /** Cuenta esperando su comprobante (referencia al documento Firestore). */
    private DocumentReference cuentaPorPagar;

    private AdaptadorPagos adaptador;
    private TextView txtEstado;
    private Button btnTabPorPagar;
    private Button btnTabPagadas;
    private RequestQueue cola;   // solo para bajar el comprobante (imagen https)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pagos);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        cola = Volley.newRequestQueue(this);

        adaptador = new AdaptadorPagos();
        RecyclerView rec = findViewById(R.id.recPagos);
        rec.setLayoutManager(new LinearLayoutManager(this));
        rec.setAdapter(adaptador);

        txtEstado = findViewById(R.id.txtEstadoPagos);
        btnTabPorPagar = findViewById(R.id.btnTabPorPagar);
        btnTabPagadas = findViewById(R.id.btnTabPagadas);
        findViewById(R.id.filaChipsPagos).setVisibility(View.GONE);   // S10: sin veredictos aquí

        btnTabPorPagar.setOnClickListener(v -> { modo = MODO_POR_PAGAR; pintar(); });
        btnTabPagadas.setOnClickListener(v -> { modo = MODO_PAGADAS; pintar(); });
        findViewById(R.id.btnVolverPagos).setOnClickListener(v ->
                Sesion.salirAlLogin(PagosActivity.this));

        txtEstado.setText(R.string.pagos_cargando);
        cargar();
    }

    private void cargar() {
        // S10.3: rol único de empresa (cliente): cada correo ve SOLO sus
        // cuentas — así los pagos nunca se mezclan entre empresas.
        FirebaseServicio.escucharOrdenesCliente(Sesion.correo(this), this::recibir);
    }

    private void recibir(List<QueryDocumentSnapshot> lista) {
        runOnUiThread(() -> {
            porPagar.clear();
            pagadas.clear();
            for (QueryDocumentSnapshot d : lista) {
                if ("pagada".equals(texto(d, "estado"))) {
                    pagadas.add(d);
                } else {
                    porPagar.add(d);
                }
            }
            Collections.sort(porPagar, (a, b) -> texto(b, "fechaLimite")
                    .compareTo(texto(a, "fechaLimite")));
            pintar();
        });
    }

    // ========================================================================
    // Pintado — pestañas y lista
    // ========================================================================

    private void pintar() {
        boolean enPorPagar = MODO_POR_PAGAR.equals(modo);
        List<QueryDocumentSnapshot> lista = enPorPagar ? porPagar : pagadas;

        btnTabPorPagar.setText(getString(R.string.tab_por_pagar, porPagar.size()));
        btnTabPagadas.setText(getString(R.string.tab_pagadas, pagadas.size()));
        btnTabPorPagar.setTextColor(ContextCompat.getColor(this,
                enPorPagar ? R.color.primario : R.color.hint_sobre_tarjeta));
        btnTabPagadas.setTextColor(ContextCompat.getColor(this,
                enPorPagar ? R.color.hint_sobre_tarjeta : R.color.primario));

        adaptador.setLista(lista);
        adaptador.notifyDataSetChanged();

        if (lista.isEmpty()) {
            txtEstado.setVisibility(View.VISIBLE);
            txtEstado.setText(enPorPagar
                    ? R.string.pagos_vacio_por_pagar : R.string.pagos_vacio_pagadas);
        } else {
            txtEstado.setVisibility(View.GONE);
        }
    }

    /** Días que faltan para la fecha límite (días julianos de Motores). */
    private int diasRestantes(String fechaLimite) {
        long jl = Motores.juliano(fechaLimite);
        long jh = Motores.juliano(hoy());
        if (jl < 0 || jh < 0) {
            return Integer.MAX_VALUE;
        }
        return (int) (jl - jh);
    }

    private String hoy() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    // ========================================================================
    // Diálogo de la cuenta — pendientes SIN foto; pagadas CON comprobante
    // ========================================================================

    private void dialogCuenta(final QueryDocumentSnapshot d) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_pago, null);
        ImageView img = v.findViewById(R.id.imgFotoPago);
        TextView txt = v.findViewById(R.id.txtInfoPago);
        boolean enPorPagar = MODO_POR_PAGAR.equals(modo);

        Double monto = d.getDouble("monto");
        StringBuilder info = new StringBuilder().append(texto(d, "codigo"))
                .append(" · ").append(texto(d, "negocio")).append('\n')
                .append(texto(d, "clienteCorreo"))
                .append(" · $").append(monto == null ? "?"
                        : String.format(Locale.US, "%.2f", monto));
        if (!texto(d, "fechaLimite").isEmpty()) {
            info.append('\n').append(getString(R.string.pago_limite,
                    texto(d, "fechaLimite")));
        }
        if (!enPorPagar && !texto(d, "pagadaEn").isEmpty()) {
            info.append('\n').append("Pagada el ").append(texto(d, "pagadaEn"));
        }
        // S9.1: la pendiente no lleva NINGUNA foto — se paga adjuntando el
        // comprobante; sin él no puede pasar a pagadas.
        if (enPorPagar) {
            img.setVisibility(View.GONE);
            info.append('\n').append(getString(R.string.pago_ayuda_comprobante));
        }
        txt.setText(info.toString());

        // La foto del COMPROBANTE se revisa en PAGADAS (URL https de Storage).
        if (!enPorPagar && !texto(d, "comprobanteUrl").isEmpty()) {
            ImageRequest foto = new ImageRequest(
                    texto(d, "comprobanteUrl"),
                    bitmap -> img.setImageBitmap(bitmap),
                    0, 0, ImageView.ScaleType.FIT_CENTER, Bitmap.Config.RGB_565,
                    error -> Toast.makeText(this,
                            "No se pudo cargar el comprobante", Toast.LENGTH_SHORT).show());
            foto.setTag("CPP_S10_IMG");
            cola.add(foto);
        }

        if (enPorPagar) {
            new AlertDialog.Builder(this, R.style.CPPDialogo)
                    .setTitle(getString(R.string.pagos_cuenta_titulo))
                    .setView(v)
                    .setPositiveButton(R.string.pago_elegir_comprobante,
                            (dlg, w) -> elegirComprobante(d))
                    .setNegativeButton(R.string.cancelar, null)
                    .show();
        } else {
            new AlertDialog.Builder(this, R.style.CPPDialogo)
                    .setTitle(getString(R.string.pagos_cuenta_titulo))
                    .setView(v)
                    .setPositiveButton(R.string.pago_desmarcar,
                            (dlg, w) -> devolverAPorPagar(d))
                    .setNegativeButton(R.string.cancelar, null)
                    .show();
        }
    }

    private void devolverAPorPagar(QueryDocumentSnapshot d) {
        Map<String, Object> cambios = new HashMap<>();
        cambios.put("estado", "pendiente");
        cambios.put("pagadaEn", "");
        d.getReference().update(cambios)
                .addOnSuccessListener(v -> Toast.makeText(this,
                        "Cuenta devuelta a «por pagar».", Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    /** Abre la galería para elegir la FOTO del comprobante de pago. */
    private void elegirComprobante(QueryDocumentSnapshot d) {
        cuentaPorPagar = d.getReference();
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQ_COMPROBANTE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.sin_app_galeria, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_COMPROBANTE || cuentaPorPagar == null) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            Toast.makeText(this, R.string.pago_cancelado, Toast.LENGTH_LONG).show();
            return;
        }
        byte[] bytes = comprimirComprobante(data.getData());
        if (bytes == null) {
            Toast.makeText(this, R.string.pago_comprobante_error, Toast.LENGTH_LONG).show();
            return;
        }

        txtEstado.setVisibility(View.VISIBLE);
        txtEstado.setText(R.string.pago_enviando_comprobante);

        final DocumentReference ref = cuentaPorPagar;
        cuentaPorPagar = null;

        FirebaseServicio.subirComprobante(ref.getId(), bytes,
                url -> ref.update(pagos(url))
                        .addOnSuccessListener(v -> runOnUiThread(() ->
                                Toast.makeText(this,
                                        "Comprobante recibido: la cuenta pasó a PAGADAS.",
                                        Toast.LENGTH_LONG).show()))
                        .addOnFailureListener(e -> runOnUiThread(() ->
                                Toast.makeText(this, "Error: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show())),
                e -> runOnUiThread(() -> Toast.makeText(this,
                        "No se pudo subir el comprobante: " + e,
                        Toast.LENGTH_LONG).show()));
    }

    private Map<String, Object> pagos(String urlComprobante) {
        Map<String, Object> cambios = new HashMap<>();
        cambios.put("estado", "pagada");
        cambios.put("pagadaEn", hoy());
        cambios.put("comprobanteUrl", urlComprobante);
        return cambios;
    }

    /** Copia a caché, decodifica ≤1600 px y comprime JPEG 85 (como facturas). */
    private byte[] comprimirComprobante(Uri uri) {
        File destino = new File(getCacheDir(), "comprobante_pago.jpg");
        try (InputStream entrada = getContentResolver().openInputStream(uri);
             FileOutputStream salida = new FileOutputStream(destino)) {
            byte[] buffer = new byte[8192];
            int leidos;
            while ((leidos = entrada.read(buffer)) > 0) {
                salida.write(buffer, 0, leidos);
            }
        } catch (Exception e) {
            return null;
        }
        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(destino.getAbsolutePath(), opciones);
        opciones.inSampleSize = 1;
        while (opciones.outWidth / opciones.inSampleSize > MAX_LADO_COMPROBANTE * 2
                || opciones.outHeight / opciones.inSampleSize > MAX_LADO_COMPROBANTE * 2) {
            opciones.inSampleSize *= 2;
        }
        opciones.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(destino.getAbsolutePath(), opciones);
        if (bitmap == null) {
            return null;
        }
        int ladoMayor = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (ladoMayor > MAX_LADO_COMPROBANTE) {
            float escala = (float) MAX_LADO_COMPROBANTE / ladoMayor;
            Bitmap escalado = Bitmap.createScaledBitmap(bitmap,
                    Math.round(bitmap.getWidth() * escala),
                    Math.round(bitmap.getHeight() * escala), true);
            if (escalado != bitmap) {
                bitmap.recycle();
            }
            bitmap = escalado;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        bitmap.recycle();
        return baos.toByteArray();
    }

    private String texto(QueryDocumentSnapshot d, String clave) {
        String s = d.getString(clave);
        return s == null ? "" : s;
    }

    // ========================================================================
    // Adaptador — una fila por cuenta
    // ========================================================================

    private class AdaptadorPagos extends RecyclerView.Adapter<AdaptadorPagos.Fila> {

        private final List<QueryDocumentSnapshot> lista = new ArrayList<>();

        void setLista(List<QueryDocumentSnapshot> nueva) {
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
            final QueryDocumentSnapshot d = lista.get(pos);
            fila.titulo.setText(texto(d, "codigo") + " · " + texto(d, "negocio"));

            Double monto = d.getDouble("monto");
            fila.sub.setText(texto(d, "clienteCorreo") + " · $"
                    + (monto == null ? "?" : String.format(Locale.US, "%.2f", monto)));

            String textoEstado;
            int color;
            boolean enPorPagar = MODO_POR_PAGAR.equals(modo);
            if (enPorPagar) {
                // RF-M7: rojo si vence en ≤3 días o ya venció (julianos local).
                int dias = diasRestantes(texto(d, "fechaLimite"));
                if (dias != Integer.MAX_VALUE && dias < 0) {
                    textoEstado = getString(R.string.pago_vencida, -dias,
                            texto(d, "fechaLimite"));
                    color = R.color.rojo_error;
                } else if (dias != Integer.MAX_VALUE && dias <= 3) {
                    textoEstado = getString(R.string.pago_vence, dias,
                            texto(d, "fechaLimite"));
                    color = R.color.rojo_error;
                } else {
                    textoEstado = getString(R.string.fila_cuenta_estado_pend);
                    color = R.color.ambar_alerta;
                }
            } else {
                textoEstado = texto(d, "pagadaEn").isEmpty()
                        ? getString(R.string.fila_cuenta_estado_pagada, "?")
                        : getString(R.string.fila_cuenta_estado_pagada, texto(d, "pagadaEn"));
                color = R.color.verde_ok;
            }
            fila.estado.setText(textoEstado);
            fila.estado.setTextColor(ContextCompat.getColor(PagosActivity.this, color));

            fila.itemView.setOnClickListener(v -> dialogCuenta(d));
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
