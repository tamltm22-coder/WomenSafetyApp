package com.example.womensafetyapp.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.womensafetyapp.R;
import com.example.womensafetyapp.adapters.PlaceAdapter;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class MapHospitalFragment extends Fragment implements OnMapReadyCallback {

    private static final int RC_LOCATION = 123;

    private GoogleMap googleMap;
    private FusedLocationProviderClient fused;
    private PlacesClient places;

    private RecyclerView rv;
    private PlaceAdapter adapter;
    private final ArrayList<PlaceAdapter.Hospital> items = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_map_hospitals, container, false);

        rv = v.findViewById(R.id.rvPlaces);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PlaceAdapter(items, item -> {
            if (googleMap == null) return;
            LatLng latLng = new LatLng(item.lat, item.lng);
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f));
        });
        rv.setAdapter(adapter);

        fused = LocationServices.getFusedLocationProviderClient(requireContext());

        if (!Places.isInitialized()) {
            // R.string.google_maps_key phải trùng với meta-data trong AndroidManifest
            Places.initialize(requireContext(), getString(R.string.google_maps_key), Locale.getDefault());
        }
        places = Places.createClient(requireContext());

        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        return v;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap gMap) {
        googleMap = gMap;
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        ensureLocationPermissionThenLoad();
    }

    private void ensureLocationPermissionThenLoad() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, RC_LOCATION);
            return;
        }
        if (googleMap != null) {
            googleMap.setMyLocationEnabled(true);
        }
        getMyLocationAndSearch();
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(rc, perms, res);
        if (rc == RC_LOCATION && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            ensureLocationPermissionThenLoad();
        }
    }

    private void getMyLocationAndSearch() {
        // Kiểm tra quyền truy cập vị trí
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, RC_LOCATION);
            return;
        }

        // Nếu đã có quyền, lấy vị trí
        fused.getLastLocation().addOnSuccessListener(loc -> {
            if (loc == null) return;
            LatLng me = new LatLng(loc.getLatitude(), loc.getLongitude());
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(me, 13f));
            fetchNearbyHospitals(me); // ← Gọi hàm bạn đã viết ở dưới
        });
    }


    /** Dùng Places SDK: lấy các địa điểm quanh bạn và lọc type HOSPITAL */
    private void fetchNearbyHospitals(LatLng me) {
        String url = String.format(Locale.US,
                "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                        "?location=%f,%f&radius=5000&keyword=hospital&key=%s",
                me.latitude, me.longitude, getString(R.string.google_maps_key));

        new Thread(() -> {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return;
                String json = response.body().string();

                JSONObject root = new JSONObject(json);
                JSONArray results = root.getJSONArray("results");

                ArrayList<PlaceAdapter.Hospital> temp = new ArrayList<>();

                for (int i = 0; i < results.length(); i++) {
                    JSONObject o = results.getJSONObject(i);
                    String id = o.optString("place_id");
                    String name = o.optString("name");
                    String address = o.optString("vicinity");

                    JSONObject loc = o.getJSONObject("geometry").getJSONObject("location");
                    double lat = loc.getDouble("lat");
                    double lng = loc.getDouble("lng");

                    float[] dist = new float[1];
                    Location.distanceBetween(me.latitude, me.longitude, lat, lng, dist);

                    temp.add(new PlaceAdapter.Hospital(id, name, address, lat, lng, dist[0]));
                }

                requireActivity().runOnUiThread(() -> {
                    items.clear();
                    items.addAll(temp);
                    adapter.notifyDataSetChanged();

                    googleMap.clear();
                    for (PlaceAdapter.Hospital h : items) {
                        googleMap.addMarker(new MarkerOptions()
                                .position(new LatLng(h.lat, h.lng))
                                .title(h.name)
                                .snippet(h.address));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    /* Nếu bạn muốn độ chính xác cao hơn (bán kính, từ khóa "hospital"):
       dùng Nearby Search (REST) qua Retrofit/OkHttp:
       https://maps.googleapis.com/maps/api/place/nearbysearch/json
       ?location=lat,lng&radius=5000&keyword=hospital&key=API_KEY
       Sau khi parse → thêm vào 'items' giống cách trên rồi notify adapter.
     */
}
