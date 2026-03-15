package com.example.womensafetyapp.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.womensafetyapp.R;
import com.example.womensafetyapp.activities.CallButtonSosActivity;
import com.example.womensafetyapp.database.ContactDatabaseHelper;
import com.example.womensafetyapp.database.SosLogDbHelper;
import com.example.womensafetyapp.geofence.DangerZone;
import com.example.womensafetyapp.geofence.DangerZonesRepository;
import com.example.womensafetyapp.models.EmergencyContact;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SosFragment extends Fragment implements OnMapReadyCallback {

    private static final int REQ_LOCATION = 1001;
    private static final int REQ_SEND_SMS = 1002;
    private static final int REQ_PERMS_TRACK = 2025;
    private static final int REQ_NOTIFY = 2033;
    private static final float FIXED_RADIUS_M = 200f;

    private MapView mapView;
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng lastLatLng = null;

    private final List<Circle> dangerCircles = new ArrayList<>();
    private final List<Marker> dangerMarkers = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sos, container, false);

        mapView = view.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack()
        );

        view.findViewById(R.id.cardQuickSms).setOnClickListener(v -> handleQuickSMS());
        view.findViewById(R.id.cardCall).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CallButtonSosActivity.class)));

        // ✅ Auto start service with full background permission check
        ensureBackgroundPermissionsThenStart();
// 🔔 Auto Quick SMS nếu được yêu cầu từ Panic Mode
        Bundle args = getArguments();
        if (args != null && args.getBoolean("autoQuickSms", false)) {
            // gọi sau khi view sẵn sàng để tránh timing issues
            view.post(this::handleQuickSMS);
        }

        return view;
    }

    // === MAP ===
    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (mMap == null) return;
        mMap.setMyLocationEnabled(true);

        fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null) {
                lastLatLng = new LatLng(loc.getLatitude(), loc.getLongitude());
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastLatLng, 15f));
                refreshDangerOverlays();
//                fetchNearbyPlaces(lastLatLng);
            }
        });
    }

    // === START / STOP LiveLocationService ===
    private void startLiveLocationService() {
        Intent i = new Intent();
        i.setClassName(requireContext().getPackageName(),
                "com.example.womensafetyapp.services.LiveLocationService");
        ContextCompat.startForegroundService(requireContext(), i);
        Toast.makeText(requireContext(), "🔴 Đang chia sẻ vị trí realtime...", Toast.LENGTH_SHORT).show();
    }

    private void stopLiveLocationService() {
        Intent i = new Intent();
        i.setClassName(requireContext().getPackageName(),
                "com.example.womensafetyapp.services.LiveLocationService");
        requireContext().stopService(i);
        Toast.makeText(requireContext(), "🟢 Dừng chia sẻ vị trí", Toast.LENGTH_SHORT).show();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // === 3️⃣ Hàm kiểm tra & xin quyền chạy nền + bỏ tối ưu pin ===
    private void ensureBackgroundPermissionsThenStart() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_PERMS_TRACK);
                return;
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
            return;
        }

        askIgnoreBatteryOptimizations();
        startLiveLocationService();
    }

    private void askIgnoreBatteryOptimizations() {
        android.content.Context ctx = requireContext();
        android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(android.content.Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(ctx.getPackageName())) {
            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + ctx.getPackageName()));
            startActivity(intent);
        }
    }

    // === 4️⃣ Xử lý kết quả xin quyền ===
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ensureBackgroundPermissionsThenStart();
            } else {
                Toast.makeText(getContext(), "Cần quyền vị trí để bật tracking.", Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (requestCode == REQ_PERMS_TRACK) {
            boolean allGranted = true;
            for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            if (allGranted) {
                ensureBackgroundPermissionsThenStart();
            } else {
                Toast.makeText(getContext(), "Cần quyền chạy nền (Background Location) để chia sẻ khi app ở nền.", Toast.LENGTH_LONG).show();
                Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(i);
            }
            return;
        }

        if (requestCode == REQ_NOTIFY) {
            ensureBackgroundPermissionsThenStart();
        }
    }

    // === SMS SOS ===
    private void handleQuickSMS() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.SEND_SMS}, REQ_SEND_SMS);
            return;
        }

        ArrayList<String> phones = getTopEmergencyPhones(3);
        if (phones.isEmpty()) {
            Toast.makeText(requireContext(), "Hãy thêm liên hệ khẩn cấp trước.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), "Đang gửi tín hiệu SOS...", Toast.LENGTH_SHORT).show();

        getFreshLocationThenRun(pos -> {
            try {
                SosLogDbHelper db = new SosLogDbHelper(requireContext());
                db.insertSos(pos.latitude, pos.longitude, System.currentTimeMillis());
                db.recomputeHotspots(7);
            } catch (Throwable e) {
                e.printStackTrace();
            }

            refreshDangerOverlays();

            String body = "SOS! Tôi đang gặp nguy hiểm.";
            if (lastLatLng != null) {
                body += " Vị trí của tôi: https://maps.google.com/?q="
                        + lastLatLng.latitude + "," + lastLatLng.longitude;
            }

            try {
                SmsManager smsManager = SmsManager.getDefault();
                ArrayList<String> parts = smsManager.divideMessage(body);
                int successCount = 0;
                for (String phone : phones) {
                    try {
                        smsManager.sendMultipartTextMessage(phone, null, parts, null, null);
                        successCount++;
                    } catch (Exception e) {
                        Log.e("SosFragment", "Gửi SMS tới " + phone + " thất bại", e);
                    }
                }
                if (successCount > 0) {
                    Toast.makeText(requireContext(), "Đã gửi SOS tới " + successCount + " liên hệ.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireContext(), "Gửi SOS thất bại.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Không thể gửi SMS. Vui lòng kiểm tra lại quyền.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void getFreshLocationThenRun(java.util.function.Consumer<LatLng> onReady) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            Toast.makeText(requireContext(), "Cần quyền vị trí để gửi kèm vị trí.", Toast.LENGTH_SHORT).show();
            return;
        }

        CancellationTokenSource cts = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(loc -> {
                    LatLng use;
                    if (loc != null) {
                        use = new LatLng(loc.getLatitude(), loc.getLongitude());
                        lastLatLng = use;
                        if (mMap != null) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(use, 16f));
                        }
                    } else if (lastLatLng != null) {
                        use = lastLatLng;
                    } else {
                        Toast.makeText(requireContext(), "Không lấy được vị trí hiện tại.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    onReady.accept(use);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Lỗi vị trí: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private ArrayList<String> getTopEmergencyPhones(int limit) {
        ContactDatabaseHelper db = new ContactDatabaseHelper(requireContext());
        ArrayList<EmergencyContact> all = db.getAllContacts();
        ArrayList<String> phones = new ArrayList<>();
        for (int i = 0; i < all.size() && phones.size() < limit; i++) {
            String p = all.get(i).getPhone();
            if (p != null && !p.trim().isEmpty()) phones.add(p.trim());
        }
        return phones;
    }

    // === Firebase Nearby Places ===
//    private void fetchNearbyPlaces(LatLng me) {
//        String apiKey = getString(R.string.google_maps_key);
//        String[] types = {"hospital", "police"};
//
//        for (String type : types) {
//            String url = String.format(Locale.US,
//                    "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
//                            "?location=%f,%f&radius=10000&type=%s&key=%s",
//                    me.latitude, me.longitude, type, apiKey);
//
//            new Thread(() -> {
//                try {
//                    OkHttpClient client = new OkHttpClient();
//                    Response response = client.newCall(new Request.Builder().url(url).build()).execute();
//                    if (!response.isSuccessful()) return;
//                    String json = response.body().string();
//                    JSONObject root = new JSONObject(json);
//                    if (!"OK".equals(root.optString("status"))) return;
//                    JSONArray results = root.getJSONArray("results");
//
//                    requireActivity().runOnUiThread(() -> {
//                        for (int i = 0; i < results.length(); i++) {
//                            try {
//                                JSONObject o = results.getJSONObject(i);
//                                JSONObject loc = o.getJSONObject("geometry").getJSONObject("location");
//                                double lat = loc.getDouble("lat");
//                                double lng = loc.getDouble("lng");
//                                String name = o.optString("name", "");
//                                String addr = o.optString("vicinity", "");
//
//                                float hue = type.equals("hospital")
//                                        ? BitmapDescriptorFactory.HUE_AZURE
//                                        : BitmapDescriptorFactory.HUE_GREEN;
//
//                                Marker marker = mMap.addMarker(new MarkerOptions()
//                                        .position(new LatLng(lat, lng))
//                                        .title(name)
//                                        .icon(BitmapDescriptorFactory.defaultMarker(hue)));
//                                marker.setTag(type + " - " + addr);
//                            } catch (Exception ignored) {}
//                        }
//                    });
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }).start();
//        }
//    }

    // === MAP LIFECYCLE ===
    @Override public void onStart() { super.onStart(); mapView.onStart(); }
    @Override public void onResume() { super.onResume(); mapView.onResume(); refreshDangerOverlays(); }
    @Override public void onPause() { super.onPause(); mapView.onPause(); }
    @Override public void onStop() { super.onStop(); mapView.onStop(); }
    @Override public void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }

    // === Danger zones display ===
    private void refreshDangerOverlays() {
        if (mMap == null) return;
        for (Circle c : dangerCircles) c.remove();
        for (Marker m : dangerMarkers) m.remove();
        dangerCircles.clear();
        dangerMarkers.clear();

        SosLogDbHelper db = new SosLogDbHelper(requireContext());
        List<SosLogDbHelper.Hotspot> hsList = db.getHotspots();
        for (SosLogDbHelper.Hotspot h : hsList) {
            int fill = dangerFillForCount(h.count);
            int stroke = dangerStrokeForCount(h.count);
            addDangerCircle(h.lat, h.lng, FIXED_RADIUS_M, fill, stroke,
                    "Khu vực nguy hiểm (" + h.count + ")");
        }

        for (DangerZone z : DangerZonesRepository.getZones()) {
            int fill = withAlpha(BASE_DANGER, 0x44);
            int stroke = withAlpha(BASE_DANGER, 0x99);
            addDangerCircle(z.lat, z.lng, z.radiusMeters, fill, stroke, z.message);
        }
    }

    private void addDangerCircle(double lat, double lng, float radius, int fill, int stroke, String title) {
        LatLng pos = new LatLng(lat, lng);
        Circle c = mMap.addCircle(new CircleOptions()
                .center(pos).radius(radius)
                .strokeColor(stroke).fillColor(fill).strokeWidth(2f));
        Marker m = mMap.addMarker(new MarkerOptions().position(pos).title(title));
        dangerCircles.add(c);
        dangerMarkers.add(m);
    }

    private static final int BASE_DANGER = 0xFFFF6A00;
    private static final int MAX_COUNT_FOR_COLOR = 10;

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private int dangerFillForCount(int count) {
        int minA = 0x22, maxA = 0xCC;
        int c = Math.max(1, Math.min(count, MAX_COUNT_FOR_COLOR));
        int a = minA + Math.round((c - 1) * (maxA - minA) / (float)(MAX_COUNT_FOR_COLOR - 1));
        return withAlpha(BASE_DANGER, a);
    }

    private int dangerStrokeForCount(int count) {
        int minA = 0x66, maxA = 0xFF;
        int c = Math.max(1, Math.min(count, MAX_COUNT_FOR_COLOR));
        int a = minA + Math.round((c - 1) * (maxA - minA) / (float)(MAX_COUNT_FOR_COLOR - 1));
        return withAlpha(BASE_DANGER, a);
    }
}
