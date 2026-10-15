package com.cpp.captura;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * PushServicio — Sprint 6: recibe los push FCM que envía api/facturas.php
 * cuando un capturista sube una factura.
 *
 * Con la app en PRIMER plano, FCM no muestra nada solo: aquí se construye
 * la notificación del sistema (canal propio, prioridad alta). Con la app
 * en segundo plano o MATADA, Android la muestra automáticamente usando los
 * mismos datos. Tocarla abre ProveedorActivity (el buzón del proveedor).
 */
public class PushServicio extends FirebaseMessagingService {

    private static final String CANAL = "cpp_facturas";

    @Override
    public void onMessageReceived(RemoteMessage mensaje) {
        RemoteMessage.Notification n = mensaje.getNotification();
        if (n == null) {
            return;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CANAL,
                    "Facturas registradas", NotificationManager.IMPORTANCE_HIGH));
        }

        Intent i = new Intent(this, ProveedorActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CANAL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(n.getTitle())
                .setContentText(n.getBody())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(n.getBody()))
                .setAutoCancel(true)
                .setContentIntent(pi);

        nm.notify((int) System.currentTimeMillis(), builder.build());
    }

    /** Si Google renueva el token del teléfono, se re-registra el proveedor. */
    @Override
    public void onNewToken(String token) {
        String proveedor = FirebaseServicio.proveedorGuardado(this);
        if (!proveedor.isEmpty()) {
            FirebaseServicio.registrarProveedor(this, proveedor);
        }
    }
}
