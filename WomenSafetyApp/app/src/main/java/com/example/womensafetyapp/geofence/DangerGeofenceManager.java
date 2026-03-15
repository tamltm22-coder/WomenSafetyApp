package com.example.womensafetyapp.geofence;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;

import com.example.womensafetyapp.database.SosLogDbHelper;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.List;

public class DangerGeofenceManager {

    public static final String ACTION_GEOFENCE_EVENT = "com.example.womensafetyapp.ACTION_GEOFENCE_EVENT";

    private final Context ctx;
    private final GeofencingClient geofencingClient;

    public DangerGeofenceManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.geofencingClient = LocationServices.getGeofencingClient(ctx);
    }

    private PendingIntent geofencePendingIntent() {
        Intent intent = new Intent(ACTION_GEOFENCE_EVENT);
        intent.setClassName(ctx, "com.example.womensafetyapp.geofence.DangerGeofenceReceiver");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(ctx, 0, intent, flags);
    }

    /** Đăng ký geofence cho danh sách hotspot tự sinh. */
    public void registerHotspotList(List<SosLogDbHelper.Hotspot> hs) {
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        if (hs == null || hs.isEmpty()) {
            // Không có hotspot để đăng ký → bỏ qua, hoặc có thể remove geofences cũ nếu muốn
            return;
        }

        List<Geofence> list = new ArrayList<>();
        for (int i = 0; i < hs.size(); i++) {
            SosLogDbHelper.Hotspot h = hs.get(i);
            list.add(new Geofence.Builder()
                    .setRequestId("HS-" + i)
                    .setCircularRegion(h.lat, h.lng, h.radius)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER
                            | Geofence.GEOFENCE_TRANSITION_DWELL)
                    .setLoiteringDelay(2 * 60 * 1000)
                    .build());
        }

        GeofencingRequest req = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(list)
                .build();

        geofencingClient.addGeofences(req, geofencePendingIntent());
    }


    /** Bỏ đăng ký tất cả geofence (nếu cần). */
//    public void unregisterAll() {
//        geofencingClient.removeGeofences(geofencePendingIntent());
//    }
}
