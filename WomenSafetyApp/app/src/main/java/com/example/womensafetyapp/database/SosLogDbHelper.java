package com.example.womensafetyapp.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SosLogDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "sos_logs.db";
    private static final int DB_VERSION = 1;

    // Bảng sự kiện SOS thô
    private static final String T_SOS = "sos_events";
    private static final String C_ID = "id";
    private static final String C_LAT = "lat";
    private static final String C_LNG = "lng";
    private static final String C_TS  = "ts"; // epoch millis

    // Bảng hotspot đã gom cụm
    private static final String T_HS = "sos_hotspots";
    private static final String H_ID = "id";
    private static final String H_LAT = "lat";
    private static final String H_LNG = "lng";
    private static final String H_RADIUS = "radius";
    private static final String H_COUNT = "count";
    private static final String H_UPDATED = "updated_ts";

    public SosLogDbHelper(@Nullable Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_SOS + " (" +
                C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                C_LAT + " REAL," +
                C_LNG + " REAL," +
                C_TS  + " INTEGER" +
                ")");

        db.execSQL("CREATE TABLE " + T_HS + " (" +
                H_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                H_LAT + " REAL," +
                H_LNG + " REAL," +
                H_RADIUS + " REAL," +
                H_COUNT + " INTEGER," +
                H_UPDATED + " INTEGER" +
                ")");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    // ==== SOS events ====
    public void insertSosEvent(double lat, double lng, long ts) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(C_LAT, lat);
        cv.put(C_LNG, lng);
        cv.put(C_TS, ts);
        db.insert(T_SOS, null, cv);
    }

    /** Lấy các event trong N ngày gần nhất (để window tính hotspot). */
    public Cursor querySosEventsSince(long sinceTs) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(T_SOS, null, C_TS + ">=?", new String[]{String.valueOf(sinceTs)}, null, null, null);
    }

    // ==== Hotspots ====
    public void replaceAllHotspots(List<Hotspot> hs) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(T_HS, null, null);
            for (Hotspot h : hs) {
                ContentValues cv = new ContentValues();
                cv.put(H_LAT, h.lat);
                cv.put(H_LNG, h.lng);
                cv.put(H_RADIUS, h.radius);
                cv.put(H_COUNT, h.count);
                cv.put(H_UPDATED, h.updatedTs);
                db.insert(T_HS, null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static class Hotspot {
        public double lat, lng;
        public float radius;
        public int count;
        public long updatedTs;
    }

    public List<Hotspot> getHotspots() {
        ArrayList<Hotspot> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(T_HS, null, null, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                do {
                    Hotspot h = new Hotspot();
                    h.lat = c.getDouble(c.getColumnIndexOrThrow(H_LAT));
                    h.lng = c.getDouble(c.getColumnIndexOrThrow(H_LNG));
                    h.radius = (float) c.getDouble(c.getColumnIndexOrThrow(H_RADIUS));
                    h.count = c.getInt(c.getColumnIndexOrThrow(H_COUNT));
                    h.updatedTs = c.getLong(c.getColumnIndexOrThrow(H_UPDATED));
                    out.add(h);
                } while (c.moveToNext());
            }
        }
        return out;
    }
    // === Ghi log SOS mới ===
    // === Ghi log SOS mới (đúng schema) ===
    public void insertSos(double lat, double lng, long ts) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(C_LAT, lat);
        cv.put(C_LNG, lng);
        cv.put(C_TS, ts);
        db.insert(T_SOS, null, cv);
    }
    /** Rebuild hotspots từ các SOS trong N ngày gần nhất. */
    public void recomputeHotspots(int daysWindow) {
        long now = System.currentTimeMillis();
        long since = now - daysWindow * 24L * 60L * 60L * 1000L;

        // Gom cụm kiểu "lưới" ~ 100–120m: làm tròn 3 chữ số thập phân
        class Agg { double sumLat, sumLng; int cnt; }
        java.util.Map<String, Agg> map = new java.util.HashMap<>();

        try (Cursor c = querySosEventsSince(since)) {
            if (c != null && c.moveToFirst()) {
                int iLat = c.getColumnIndexOrThrow(C_LAT);
                int iLng = c.getColumnIndexOrThrow(C_LNG);
                do {
                    double lat = c.getDouble(iLat);
                    double lng = c.getDouble(c.getColumnIndexOrThrow(C_LNG));
                    String key = String.format(java.util.Locale.US, "%.3f,%.3f",
                            Math.round(lat * 1000.0) / 1000.0,
                            Math.round(lng * 1000.0) / 1000.0);
                    Agg a = map.get(key);
                    if (a == null) { a = new Agg(); map.put(key, a); }
                    a.sumLat += lat; a.sumLng += lng; a.cnt++;
                } while (c.moveToNext());
            }
        }

        java.util.List<Hotspot> out = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Agg> e : map.entrySet()) {
            Agg a = e.getValue();
            Hotspot h = new Hotspot();
            h.lat = a.sumLat / a.cnt;
            h.lng = a.sumLng / a.cnt;
            // bán kính tuỳ theo tần suất, giới hạn 300m
            h.radius = Math.min(200f + a.cnt * 15f, 300f);
            h.count = a.cnt;
            h.updatedTs = now;
            out.add(h);
        }

        replaceAllHotspots(out);
    }


}
