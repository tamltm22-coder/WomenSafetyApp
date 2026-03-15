package com.example.womensafetyapp.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.womensafetyapp.R;
import com.google.android.gms.location.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Service chạy nền để gửi vị trí realtime của người dùng lên Firebase.
 * Path: users/{uid}/live_location/{lat,lng,timestamp}
 */
public class LiveLocationService extends Service {
    public static final String ACTION_STOP = "com.example.womensafetyapp.ACTION_STOP";
    private boolean started = false;// ngăn gọi start nhiều lần
    private static final String TAG = "LiveLocationService";
    private static final String CHANNEL_ID = "live_location_channel";

    private FusedLocationProviderClient fusedClient;
    private LocationCallback callback;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        Log.d(TAG, "Service created");
    }

    /**
     * Quan trọng: trả START_STICKY để hệ thống có thể restart service nếu bị kill khi app nền.
     * Đồng thời khởi tạo foreground notification + location updates (1 lần).
     */

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand, started=" + started);

        // ✅ Nếu người dùng bấm STOP trên notification
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Log.d(TAG, "📴 Nhận ACTION_STOP, dừng service.");
            stopSelf();
            return START_NOT_STICKY;
        }

        // ✅ Khởi động service nếu chưa chạy
        if (!started) {
            startForegroundNotification(); // tạo notification có nút STOP
            startLocationUpdates();
            started = true;
        }

        return START_STICKY; // 🔴 giúp chạy nền bền vững
    }

    /** Hiển thị thông báo foreground để tránh bị Android kill */
    private void startForegroundNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Live Location Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Cập nhật vị trí realtime để người thân theo dõi.");
            nm.createNotificationChannel(channel);
        }

        // ✅ Intent để xử lý khi người dùng bấm nút STOP
        Intent stopIntent = new Intent(this, LiveLocationService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this,
                1,
                stopIntent,
                Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        // ✅ Notification có nút STOP
        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🔴 Đang chia sẻ vị trí")
                .setContentText("Vị trí của bạn đang được cập nhật liên tục…")
                .setSmallIcon(R.drawable.ic_location)
                .setOngoing(true)
                .addAction(0, "STOP", stopPending) // 👈 Thêm nút STOP ở đây
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notif);
    }


    /** Bắt đầu cập nhật vị trí liên tục */
    private void startLocationUpdates() {
        LocationRequest req = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5_000 // 5 giây/lần
        )
                .setMinUpdateDistanceMeters(25) // di chuyển ≥25m mới update
                .build();

        callback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                for (Location loc : result.getLocations()) {
                    updateFirebase(loc);
                }
            }
        };

        // Kiểm tra quyền
        boolean fineOk = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseOk = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!fineOk && !coarseOk) {
            Log.w(TAG, "⚠️ Không có quyền vị trí. Dừng service.");
            stopSelf();
            return;
        }

        // Chạy cập nhật
        fusedClient.requestLocationUpdates(req, callback, getMainLooper());
        Log.d(TAG, "Location updates started");
    }

    /** Gửi vị trí mới lên Firebase */
    private void updateFirebase(Location loc) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) {
            Log.e(TAG, "⚠️ Không tìm thấy UID, dừng service.");
            stopSelf();
            return;
        }

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .child("live_location");

        ref.child("lat").setValue(loc.getLatitude());
        ref.child("lng").setValue(loc.getLongitude());
        ref.child("timestamp").setValue(System.currentTimeMillis());

        Log.d(TAG, "📍 Vị trí cập nhật: " + loc.getLatitude() + ", " + loc.getLongitude());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedClient != null && callback != null) {
            fusedClient.removeLocationUpdates(callback);
        }
        started = false;
        Log.d(TAG, "🟢 LiveLocationService stopped.");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Service không bind, chỉ start/stop
    }
}
