package com.example.womensafetyapp.utils;

import android.content.Context;
import android.database.Cursor;

import com.example.womensafetyapp.database.SosLogDbHelper;

import java.util.ArrayList;
import java.util.List;

public class SosHotspotGenerator {

    // Tham số có thể tuỳ chỉnh trong Settings
    private static final float CLUSTER_RADIUS_M = 200f; // bán kính gom cụm
    private static final int THRESHOLD_COUNT = 3;       // tối thiểu 3 lần bấm
    private static final int WINDOW_DAYS = 14;          // 14 ngày gần nhất
    private static final float HOTSPOT_GEOFENCE_RADIUS = 220f;

    static class Pt { double lat, lng; long ts; }
    static class Cluster { double lat, lng; int count; }

    /** Đọc sự kiện trong WINDOW_DAYS, gom cụm, ghi vào bảng hotspots, và trả về danh sách. */
    public static List<SosLogDbHelper.Hotspot> rebuildHotspots(Context ctx) {
        long now = System.currentTimeMillis();
        long since = now - WINDOW_DAYS * 24L * 60L * 60L * 1000L;

        SosLogDbHelper db = new SosLogDbHelper(ctx);
        List<Pt> pts = new ArrayList<>();

        try (Cursor c = db.querySosEventsSince(since)) {
            if (c != null && c.moveToFirst()) {
                do {
                    Pt p = new Pt();
                    p.lat = c.getDouble(c.getColumnIndexOrThrow("lat"));
                    p.lng = c.getDouble(c.getColumnIndexOrThrow("lng"));
                    p.ts  = c.getLong(c.getColumnIndexOrThrow("ts"));
                    pts.add(p);
                } while (c.moveToNext());
            }
        }

        // Gom cụm đơn giản: tuyến tính, nhập vào cụm gần nhất nếu trong bán kính
        List<Cluster> clusters = new ArrayList<>();
        for (Pt p : pts) {
            Cluster nearest = null;
            double nearestD = Double.MAX_VALUE;
            for (Cluster cl : clusters) {
                double d = haversineMeters(p.lat, p.lng, cl.lat, cl.lng);
                if (d < nearestD) { nearestD = d; nearest = cl; }
            }
            if (nearest != null && nearestD <= CLUSTER_RADIUS_M) {
                nearest.lat = (nearest.lat * nearest.count + p.lat) / (nearest.count + 1);
                nearest.lng = (nearest.lng * nearest.count + p.lng) / (nearest.count + 1);
                nearest.count += 1;
            } else {
                Cluster cl = new Cluster();
                cl.lat = p.lat; cl.lng = p.lng; cl.count = 1;
                clusters.add(cl);
            }
        }

        ArrayList<SosLogDbHelper.Hotspot> out = new ArrayList<>();
        for (Cluster cl : clusters) {
            if (cl.count >= THRESHOLD_COUNT) {
                SosLogDbHelper.Hotspot h = new SosLogDbHelper.Hotspot();
                h.lat = cl.lat;
                h.lng = cl.lng;
                h.radius = HOTSPOT_GEOFENCE_RADIUS;
                h.count = cl.count;
                h.updatedTs = now;
                out.add(h);
            }
        }

        db.replaceAllHotspots(out);
        return out;
    }

    /** Haversine ~ mét. */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2-lat1);
        double dLon = Math.toRadians(lon2-lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2)*Math.sin(dLon/2);
        double c = 2*Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R*c;
    }
}
