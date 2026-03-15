package com.example.womensafetyapp.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.example.womensafetyapp.R;
import com.example.womensafetyapp.database.ContactDatabaseHelper;
import com.example.womensafetyapp.models.UserProfile;
import com.example.womensafetyapp.utils.NetworkUtils;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class ProfileDetailFragment extends Fragment {

    private ContactDatabaseHelper db;
    private DatabaseReference profileRef;
    private DatabaseReference userRootRef;     // /users/{uid}
    private DatabaseReference medLegacyRef;    // /medical_info/{uid} (cũ, để migrate)

    private TextView tvName, tvPhone, tvEmail, tvBlood, tvAllergies, tvConds, tvMeds;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.activity_profile_details, container, false);

        Toolbar toolbar = v.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(view ->
                requireActivity().getSupportFragmentManager().popBackStack());

        tvName      = v.findViewById(R.id.tvFullName);
        tvPhone     = v.findViewById(R.id.tvPhone);
        tvEmail     = v.findViewById(R.id.tvEmail);
        tvBlood     = v.findViewById(R.id.tvBloodType);
        tvAllergies = v.findViewById(R.id.tvAllergies);
        tvConds     = v.findViewById(R.id.tvConditions);
        tvMeds      = v.findViewById(R.id.tvMedications);

        db = new ContactDatabaseHelper(requireContext());

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) {
            // chưa đăng nhập: hiển thị local nếu có
            bind(db.getProfile());
            return v;
        }

        FirebaseDatabase fdb = FirebaseDatabase.getInstance();
        userRootRef  = fdb.getReference("users").child(uid);
        profileRef   = userRootRef.child("profile");
        medLegacyRef = fdb.getReference("medical_info").child(uid); // để migrate nếu cần

        // 1) Load local trước
        bind(db.getProfile());

        // 2) Nếu online: kéo Firebase → local, có kèm migrate schema cũ nếu thiếu
        if (NetworkUtils.isNetworkAvailable(requireContext())) {
            fetchRemoteThenBindWithMigration();
        }

        ExtendedFloatingActionButton fab = v.findViewById(R.id.fabChange);
        fab.setOnClickListener(view -> {
            UserProfile current = db.getProfile();
            Fragment edit = ProfileFragment.newInstance(current);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, edit)
                    .addToBackStack(null)
                    .commit();
        });

        return v;
    }

    private void fetchRemoteThenBindWithMigration() {
        // cố gắng đọc /users/{uid}/profile trước
        profileRef.get().addOnSuccessListener(pSnap -> {
            UserProfile p = pSnap.getValue(UserProfile.class);
            if (p != null) {
                db.upsertProfile(p);
                bind(p);
                return;
            }

            // Chưa có profile → thử migrate từ dữ liệu cũ (users root + medical_info)
            userRootRef.get().addOnSuccessListener(uSnap -> {
                medLegacyRef.get().addOnSuccessListener(mSnap -> {
                    // map gom về profile
                    Map<String, Object> map = new HashMap<>();

                    Object fullFromUsers = uSnap.child("full_name").getValue();
                    if (fullFromUsers == null) fullFromUsers = uSnap.child("fullname").getValue(); // phòng trường hợp trước kia dùng "fullname"
                    Object phone = uSnap.child("phone").getValue();
                    Object email = uSnap.child("email").getValue();

                    Object blood   = mSnap.child("blood_type").getValue();
                    Object allerg  = mSnap.child("allergies").getValue();
                    Object cond    = mSnap.child("conditions").getValue();
                    Object meds    = mSnap.child("medications").getValue();

                    if (fullFromUsers != null) map.put("full_name", fullFromUsers);
                    if (phone != null)        map.put("phone", phone);
                    if (email != null)        map.put("email", email);
                    if (blood != null)        map.put("blood_type", blood);
                    if (allerg != null)       map.put("allergies", allerg);
                    if (cond != null)         map.put("conditions", cond);
                    if (meds != null)         map.put("medications", meds);

                    if (map.isEmpty()) {
                        // không có gì để migrate, giữ local hiện tại
                        return;
                    }

                    // Ghi về /users/{uid}/profile theo schema mới
                    profileRef.updateChildren(map).addOnSuccessListener(unused -> {
                        // đọc lại theo model để bind & lưu local
                        profileRef.get().addOnSuccessListener(sn -> {
                            UserProfile migrated = sn.getValue(UserProfile.class);
                            if (migrated != null) {
                                db.upsertProfile(migrated);
                                bind(migrated);
                            }
                        });
                    });
                });
            });
        });
    }

    private void bind(@Nullable UserProfile p) {
        if (p == null) return;
        tvName.setText(emptyDash(p.getFullName()));
        tvPhone.setText(emptyDash(p.getPhone()));
        tvEmail.setText(emptyDash(p.getEmail()));
        tvBlood.setText(emptyDash(p.getBloodType()));
        tvAllergies.setText(emptyDash(p.getAllergies()));
        tvConds.setText(emptyDash(p.getConditions()));
        tvMeds.setText(emptyDash(p.getMedications()));
    }

    private String emptyDash(String s) {
        return (s == null || s.trim().isEmpty()) ? "—" : s;
    }
}
