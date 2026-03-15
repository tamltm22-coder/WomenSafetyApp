// App.java
package com.example.womensafetyapp;

import android.app.Application;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;

public class App extends Application {
    public static PlacesClient PLACES;

    @Override public void onCreate() {
        super.onCreate();
        if (!Places.isInitialized()) {
            Places.initialize(this, getString(R.string.google_maps_key)); // dùng app context
        }
        PLACES = Places.createClient(this); // tạo 1 lần
    }
}
