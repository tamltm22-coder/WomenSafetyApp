package com.example.womensafetyapp.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.core.app.ActivityCompat;

import com.example.womensafetyapp.database.SosLogDbHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

public class SosLogger {

    /** Ghi lại vị trí hiện tại (nếu có) vào bảng sos_events. */
    public static void logSosNow(Context ctx) {
        long now = System.currentTimeMillis();
        FusedLocationProviderClient flpc = LocationServices.getFusedLocationProviderClient(ctx);

        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            flpc.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null) {
                    persist(ctx, loc, now);
                }
            });
        }
    }

    private static void persist(Context ctx, Location loc, long ts) {
        SosLogDbHelper db = new SosLogDbHelper(ctx);
        db.insertSosEvent(loc.getLatitude(), loc.getLongitude(), ts);
    }
}
