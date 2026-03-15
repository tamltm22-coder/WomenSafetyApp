package com.example.womensafetyapp.geofence;

public class DangerZone {
    public final String id;
    public final double lat, lng;
    public final float radiusMeters;
    public final String message;
    public DangerZone(String id, double lat, double lng, float radiusMeters, String message) {
        this.id = id; this.lat = lat; this.lng = lng; this.radiusMeters = radiusMeters; this.message = message;
    }
}
