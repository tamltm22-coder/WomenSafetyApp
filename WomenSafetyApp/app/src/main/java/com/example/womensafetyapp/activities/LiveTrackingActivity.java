package com.example.womensafetyapp.activities;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.womensafetyapp.R;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

public class LiveTrackingActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "LiveTrackingActivity";
    private MapView mapView;
    private GoogleMap googleMap;
    private Marker marker;
    private DatabaseReference locRef;
    private ValueEventListener listener;
    private String targetUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_tracking);

        // ✅ Tự động lấy UID của tài khoản hiện tại
        targetUid = getIntent().getStringExtra("targetUid");
        if (targetUid == null || targetUid.trim().isEmpty()) {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                targetUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            } else {
                Log.e(TAG, "❌ Không tìm thấy UID người dùng.");
                finish();
                return;
            }
        }

        mapView = findViewById(R.id.mapViewLive);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap gMap) {
        this.googleMap = gMap;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        subscribeToRealtimeLocation();
    }

    // ✅ Theo dõi vị trí realtime từ Firebase
    private void subscribeToRealtimeLocation() {
        locRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(targetUid)
                .child("live_location");

        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                Double lat = snapshot.child("lat").getValue(Double.class);
                Double lng = snapshot.child("lng").getValue(Double.class);
                if (lat == null || lng == null) return;

                LatLng position = new LatLng(lat, lng);
                runOnUiThread(() -> updateMarker(position));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "⚠️ Lỗi realtime listener: " + error.getMessage());
            }
        };

        locRef.addValueEventListener(listener);
    }

    private void updateMarker(LatLng pos) {
        if (googleMap == null) return;

        if (marker == null) {
            marker = googleMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("📍 Vị trí hiện tại")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE))
            );
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));
        } else {
            marker.setPosition(pos);
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(pos));
        }
    }

    // --- Vòng đời MapView ---
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onPause() { mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { mapView.onStop(); super.onStop(); }
    @Override protected void onDestroy() {
        if (locRef != null && listener != null) locRef.removeEventListener(listener);
        mapView.onDestroy();
        super.onDestroy();
    }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}
