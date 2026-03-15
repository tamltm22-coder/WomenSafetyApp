package com.example.womensafetyapp.geofence;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.womensafetyapp.utils.NotiHelper;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

public class DangerGeofenceReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) return;

        int transition = event.getGeofenceTransition();
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER
                && transition != Geofence.GEOFENCE_TRANSITION_DWELL) return;

        // Có thể duyệt qua danh sách triggering geofences để cá nhân hoá
        NotiHelper.showDangerNotification(context,
                "Bạn đang đi vào khu vực có cảnh báo an toàn. Hãy cẩn thận quan sát!");
    }
}
