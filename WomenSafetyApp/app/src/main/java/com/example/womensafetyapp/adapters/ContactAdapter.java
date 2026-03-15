package com.example.womensafetyapp.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.womensafetyapp.R;
import com.example.womensafetyapp.models.EmergencyContact;

import java.util.ArrayList;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.VH> {

    private final List<EmergencyContact> data;       // <— chỉ khai báo 1 lần
    private final OnDeleteClick onDelete;            // <— chỉ khai báo 1 lần

    public interface OnDeleteClick {
        void onDelete(EmergencyContact item, int position);
    }

    public ContactAdapter(List<EmergencyContact> data, OnDeleteClick onDelete) {
        this.data = (data != null) ? data : new ArrayList<>();
        this.onDelete = onDelete;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvPhone, tvRelation;
        ImageView ivDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName     = itemView.findViewById(R.id.tvContactName);
            tvPhone    = itemView.findViewById(R.id.tvContactPhone);
            tvRelation = itemView.findViewById(R.id.tvContactRelation);
            ivDelete   = itemView.findViewById(R.id.ivDeleteContact);
        }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        EmergencyContact c = data.get(pos);
        h.tvName.setText(c.getName());
        h.tvPhone.setText(c.getPhone());
        h.tvRelation.setText(c.getRelation());

        h.ivDelete.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition(); // thay cho getAdapterPosition()
            if (p == RecyclerView.NO_POSITION) return;
            if (onDelete != null) onDelete.onDelete(data.get(p), p);
        });
    }

    @Override public int getItemCount() { return data.size(); }
}
