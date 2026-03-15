package com.example.womensafetyapp.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.womensafetyapp.R;

public class NotiHelper {
    private static final String CH_ID = "danger_zone_channel";

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "Cảnh báo khu vực nguy hiểm", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    public static void showDangerNotification(Context ctx, String content) {
        ensureChannel(ctx);
        int icon = R.mipmap.ic_launcher; // nếu chưa có, đổi sang R.mipmap.ic_launcher
        try {
            ctx.getResources().getResourceName(icon);
        } catch (Exception e) {
            icon = R.mipmap.ic_launcher;
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH_ID)
                .setSmallIcon(icon)
                .setContentTitle("Cảnh báo an toàn")
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((int) System.currentTimeMillis(), b.build());
    }
}
