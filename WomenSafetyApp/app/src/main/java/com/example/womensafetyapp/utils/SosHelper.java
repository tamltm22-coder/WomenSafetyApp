package com.example.womensafetyapp.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import com.example.womensafetyapp.database.ContactDatabaseHelper;
import com.example.womensafetyapp.models.EmergencyContact;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

public class SosHelper {

    public interface LocationCallback {
        void onLocationReceived(LatLng location);
    }

    /** Lấy tối đa 'limit' số điện thoại liên hệ khẩn cấp từ SQLite. */
    public static ArrayList<String> getTopEmergencyPhones(Context context, int limit) {
        ArrayList<String> phones = new ArrayList<>();
        ContactDatabaseHelper db = new ContactDatabaseHelper(context);
        ArrayList<EmergencyContact> all = db.getAllContacts();
        for (int i = 0; i < all.size() && phones.size() < limit; i++) {
            String p = all.get(i).getPhone();
            if (p != null) {
                p = p.trim();
                if (!p.isEmpty()) phones.add(p);
            }
        }
        return phones;
    }

    /** Xây nội dung tin nhắn khẩn cấp với vị trí. */
    public static String buildEmergencyMessage(Context context, LatLng location, String reason) {
        String prefix = "SOS! " + reason + ".";
        if (location != null) {
            return prefix + " Đây là vị trí của tôi: " + buildMapsLink(location);
        } else {
            return prefix + " Tôi chưa lấy được vị trí, hãy gọi lại ngay!";
        }
    }

    /** Tạo link Google Maps từ tọa độ. */
    private static String buildMapsLink(LatLng latLng) {
        return "https://maps.google.com/?q=" + latLng.latitude + "," + latLng.longitude;
    }

    /** Lấy vị trí hiện tại (nếu có) và gọi callback. */
    @SuppressLint("MissingPermission")
    public static void getCurrentLocation(Context context, LocationCallback callback) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            callback.onLocationReceived(null);
            return;
        }

        // Thử lấy last known location trước
        Location lastGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        Location bestLocation = getBetterLocation(lastGPS, lastNetwork);

        if (bestLocation != null) {
            callback.onLocationReceived(new LatLng(bestLocation.getLatitude(), bestLocation.getLongitude()));
            return;
        }

        // Nếu không có last location, request một lần
        boolean hasGPS = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean hasNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!hasGPS && !hasNetwork) {
            callback.onLocationReceived(null);
            return;
        }

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                locationManager.removeUpdates(this);
                callback.onLocationReceived(new LatLng(location.getLatitude(), location.getLongitude()));
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };

        if (hasGPS) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 0, 0, listener);
        }
        if (hasNetwork) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 0, 0, listener);
        }

        // Set timeout để không đợi quá lâu
        new android.os.Handler().postDelayed(() -> {
            locationManager.removeUpdates(listener);
            Location last = getBetterLocation(
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            );
            if (last != null) {
                callback.onLocationReceived(new LatLng(last.getLatitude(), last.getLongitude()));
            } else {
                callback.onLocationReceived(null);
            }
        }, 10000); // timeout 10 giây
    }

    /** So sánh 2 vị trí và trả về vị trí tốt hơn dựa trên độ chính xác và thời gian. */
    private static Location getBetterLocation(Location loc1, Location loc2) {
        if (loc1 == null) return loc2;
        if (loc2 == null) return loc1;

        long timeDelta = loc1.getTime() - loc2.getTime();
        boolean isSignificantlyNewer = timeDelta > 1000 * 60 * 2; // 2 phút
        boolean isSignificantlyOlder = timeDelta < -1000 * 60 * 2;
        boolean isNewer = timeDelta > 0;

        if (isSignificantlyNewer) return loc1;
        if (isSignificantlyOlder) return loc2;

        if (loc1.getAccuracy() < loc2.getAccuracy()) {
            return isNewer ? loc1 : loc2;
        } else {
            return !isNewer ? loc2 : loc1;
        }
    }
}
