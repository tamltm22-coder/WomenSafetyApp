package com.example.womensafetyapp.geofence;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.womensafetyapp.database.SosLogDbHelper;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Nếu app có toggle enable/disable thì đọc SharedPrefs ở đây.
        // Ở bản đơn giản này: nếu có hotspot -> đăng ký lại.
        SosLogDbHelper db = new SosLogDbHelper(context);
        new DangerGeofenceManager(context).registerHotspotList(db.getHotspots());
    }
}
