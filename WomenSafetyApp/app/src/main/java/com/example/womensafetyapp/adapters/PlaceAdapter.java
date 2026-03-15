package com.example.womensafetyapp.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.womensafetyapp.R;

import java.util.List;
import java.util.Locale;

public class PlaceAdapter extends RecyclerView.Adapter<PlaceAdapter.VH> {
    public interface OnClick { void onClick(Hospital item); }
    private final List<Hospital> data; private final OnClick onClick;

    public static class Hospital {
        public final String id, name, address;
        public final double lat, lng, distanceMeters;
        public Hospital(String id, String name, String address, double lat, double lng, double d){
            this.id=id; this.name=name; this.address=address; this.lat=lat; this.lng=lng; this.distanceMeters=d;
        }
    }

    public PlaceAdapter(List<Hospital> data, OnClick onClick){
        this.data=data; this.onClick=onClick;
    }
    static class VH extends RecyclerView.ViewHolder {
        TextView tvName,tvAddr,tvDist;
        VH(View v){ super(v);
            tvName=v.findViewById(R.id.tvName);
            tvAddr=v.findViewById(R.id.tvAddress);
            tvDist=v.findViewById(R.id.tvDistance);
        }
    }
    @NonNull
    @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v){
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_place,p,false));
    }
    @Override public void onBindViewHolder(@NonNull VH h,int pos){
        Hospital it=data.get(pos);
        h.tvName.setText(it.name);
        h.tvAddr.setText(it.address);
        h.tvDist.setText(String.format(Locale.getDefault(),"%.1f km", it.distanceMeters/1000.0));
        h.itemView.setOnClickListener(v-> { if(onClick!=null) onClick.onClick(it); });
    }
    @Override public int getItemCount(){ return data.size(); }
}
