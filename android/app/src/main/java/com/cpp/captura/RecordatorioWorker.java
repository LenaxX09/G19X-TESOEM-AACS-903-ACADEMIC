package com.cpp.captura;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * RecordatorioWorker — S10.4: recordatorios de pago para la empresa cliente.
 *
 * SIN SERVIDOR (como todo el S10): WorkManager despierta al teléfono UNA VEZ
 * AL DÍA (más un chequeo extra cada vez que la app arranca, para no perder el
 * aviso si pasaron días sin abrirla), consulta en Firestore las cuentas de
 * `ordenes` PENDIENTES dirigidas al correo de la empresa con sesión activa y
 * cuando una está a 3 días de vencer, a 1 día o ya VENCIDA lanza una
 * notificación del canal «recordatorios» que abre Cuentas por pagar.
 *
 * Anti-spam: cada aviso (cuenta + nivel + HOY) se guarda en prefs; si hoy ya
 * se avisó esa cuenta a ese nivel, no se vuelve a avisar.
 */
public class RecordatorioWorker extends Worker {

    private static final String CANAL = "recordatorios";
    private static final String PREFS = "cpp_recordatorios";

    public RecordatorioWorker(@NonNull Context contexto, @NonNull WorkerParameters params) {
        super(contexto, params);
    }

    /**
     * Programa el chequeo DIARIO (KEEP: si ya estaba programado, no se duplica)
     * + un chequeo inmediato por arranque de la app. Se llama desde MainActivity
     * (la pantalla de la empresa cliente); si no hay sesión de cliente el
     * worker termina sin hacer nada, así que llamarlo «de más» es inofensivo.
     */
    public static void programar(Context c) {
        try {
            // Con RED: JobScheduler no despierta el trabajo hasta haber red y,
            // al correr el job, le CONCEDE red aunque la app esté en segundo
            // plano (sin esto, los ahorros de batería del fabricante bloquean
            // la red de la app dormida y el recordatorio no sale).
            Constraints conRed = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();
            WorkManager wm = WorkManager.getInstance(c);
            PeriodicWorkRequest diario = new PeriodicWorkRequest.Builder(
                    RecordatorioWorker.class, 1, TimeUnit.DAYS)
                    .setConstraints(conRed)
                    .build();
            wm.enqueueUniquePeriodicWork("recordatorios_diario",
                    // UPDATE: si ya existía (p. ej. tras actualizar la app),
                    // refresca periodicidad/restricciones en vez de quedarse
                    // con la especificación vieja.
                    ExistingPeriodicWorkPolicy.UPDATE, diario);
            OneTimeWorkRequest ahora = new OneTimeWorkRequest.Builder(
                    RecordatorioWorker.class)
                    .setConstraints(conRed)
                    .build();
            // REPLACE: si quedó un chequeo viejo pendiente (reintentos), se
            // sustituye por esta especificación (con restricción de red).
            wm.enqueueUniqueWork("recordatorio_chequeo",
                    ExistingWorkPolicy.REPLACE, ahora);
        } catch (Exception ignored) {
            // best-effort (patrón del proyecto): sin WorkManager no hay
            // recordatorio, pero la app sigue funcionando igual.
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        String correo = Sesion.correo(c);
        if (correo.isEmpty() || Sesion.esProveedor(c) || !Sesion.activa(c)) {
            return Result.success();   // sin sesión de CLIENTE: nada que recordar
        }

        QuerySnapshot valor;
        try {
            // El worker vive en un hilo de fondo: la consulta puede ser
            // síncrona (Tasks.await), a diferencia de los listeners de UI.
            valor = Tasks.await(FirebaseFirestore.getInstance().collection("ordenes")
                    .whereEqualTo("clienteCorreo", correo).get());
        } catch (Exception e) {
            return Result.retry();     // red intermitente: reintentar con backoff
        }

        long hoyJuliano = Motores.juliano(hoy());
        for (QueryDocumentSnapshot d : valor) {
            if ("pagada".equals(texto(d, "estado"))) {
                continue;              // solo las PENDIENTES generan recordatorio
            }
            long limite = Motores.juliano(texto(d, "fechaLimite"));
            if (limite < 0 || hoyJuliano < 0) {
                continue;              // fecha ilegible: mejor callar (como RF-M7)
            }
            int dias = (int) (limite - hoyJuliano);
            if (dias == 3 || dias == 1 || dias < 0) {
                notificar(c, d, dias);
            }
        }
        return Result.success();
    }

    // ========================================================================
    // Notificación (canal propio; mismo patrón de PushServicio)
    // ========================================================================

    private void notificar(Context c, QueryDocumentSnapshot d, int dias) {
        SharedPreferences prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // S10.6: vencida SIN reabrir = pago CERRADO → otro mensaje (acuerdo).
        boolean cerrada = dias < 0
                && !Boolean.TRUE.equals(d.getBoolean("reabierta"));
        String nivel = cerrada ? "cerrada"
                : (dias < 0 ? "vencida" : String.valueOf(dias));
        String llave = d.getId() + "|" + nivel;
        if (hoy().equals(prefs.getString(llave, ""))) {
            return;                    // hoy ya se avisó esta cuenta a este nivel
        }

        // Android 13+: sin el permiso en runtime las notificaciones no se ven.
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CANAL,
                    c.getString(R.string.recordar_canal),
                    NotificationManager.IMPORTANCE_DEFAULT));
        }

        String codigo = texto(d, "codigo");
        Intent i = new Intent(c, PagosActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, codigo.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String titulo;
        String cuerpo;
        if (cerrada) {
            titulo = c.getString(R.string.recordar_cerrada_titulo, codigo);
            cuerpo = c.getString(R.string.recordar_cerrada_cuerpo,
                    texto(d, "negocio"), texto(d, "fechaLimite"));
        } else {
            int tituloRes = dias < 0 ? R.string.recordar_vencida
                    : (dias == 1 ? R.string.recordar_uno : R.string.recordar_tres);
            Double monto = d.getDouble("monto");
            titulo = c.getString(tituloRes, codigo);
            cuerpo = c.getString(R.string.recordar_cuerpo,
                    monto == null ? "?" : String.format(Locale.US, "%.2f", monto),
                    texto(d, "negocio"),
                    texto(d, "fechaLimite"));
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(c, CANAL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(titulo)
                .setContentText(cuerpo)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(cuerpo))
                .setAutoCancel(true)
                .setContentIntent(pi);

        // ID estable por cuenta: si volviera a avisar, actualiza en vez de apilar.
        nm.notify(d.getId().hashCode(), builder.build());
        prefs.edit().putString(llave, hoy()).apply();
    }

    // ========================================================================
    // Utilidades
    // ========================================================================

    private String hoy() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private String texto(QueryDocumentSnapshot d, String clave) {
        String s = d.getString(clave);
        return s == null ? "" : s;
    }
}
