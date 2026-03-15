package com.example.womensafetyapp.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.womensafetyapp.R;
import com.example.womensafetyapp.dao.MedicalInfoDao;
import com.example.womensafetyapp.dao.UserDao;
import com.example.womensafetyapp.firebase.FirebaseSyncManager;
import com.example.womensafetyapp.models.MedicalInfo;
import com.example.womensafetyapp.models.User;
import com.example.womensafetyapp.models.UserProfile;
import com.example.womensafetyapp.utils.NetworkUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ProfileFragment extends Fragment {

    private EditText etFullname, etPhone, etEmail, etBloodType, etAllergies, etConditions, etMedications;
    private UserDao userDao;
    private MedicalInfoDao medicalInfoDao;
    private FirebaseSyncManager syncManager;
    private String currentUid;

    // Factory nhận dữ liệu từ Details
    public static ProfileFragment newInstance(@Nullable UserProfile profile) {
        ProfileFragment fragment = new ProfileFragment();
        if (profile != null) {
            Bundle args = new Bundle();
            args.putString("fullName", profile.getFullName());
            args.putString("phone", profile.getPhone());
            args.putString("email", profile.getEmail());
            args.putString("bloodType", profile.getBloodType());
            args.putString("allergies", profile.getAllergies());
            args.putString("conditions", profile.getConditions());
            args.putString("medications", profile.getMedications());
            fragment.setArguments(args);
        }
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Ánh xạ view
        etFullname   = view.findViewById(R.id.etFullname);
        etPhone      = view.findViewById(R.id.etPhone);
        etEmail      = view.findViewById(R.id.etEmail);
        etBloodType  = view.findViewById(R.id.etBloodType);
        etAllergies  = view.findViewById(R.id.etAllergies);
        etConditions = view.findViewById(R.id.etConditions);
        etMedications= view.findViewById(R.id.etMedications);
        Button btnSave = view.findViewById(R.id.btnSave);
        Toolbar toolbar = view.findViewById(R.id.toolbarProfile);

        // Prefill từ arguments (nếu được mở từ ProfileDetailFragment)
        if (getArguments() != null) {
            etFullname.setText( getArguments().getString("fullName", "") );
            etPhone.setText(    getArguments().getString("phone", "") );
            etEmail.setText(    getArguments().getString("email", "") );
            etBloodType.setText(getArguments().getString("bloodType", "") );
            etAllergies.setText(getArguments().getString("allergies", "") );
            etConditions.setText(getArguments().getString("conditions", "") );
            etMedications.setText(getArguments().getString("medications", "") );
        }

        toolbar.setNavigationOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack()
        );

        userDao = new UserDao(requireContext());
        medicalInfoDao = new MedicalInfoDao(requireContext());
        syncManager = new FirebaseSyncManager();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) currentUid = user.getUid();

        btnSave.setOnClickListener(v -> saveProfile());

        return view;
    }

    private void saveProfile() {
        if (currentUid == null) {
            Toast.makeText(getContext(), "Please login again!", Toast.LENGTH_SHORT).show();
            return;
        }

        String fullname   = etFullname.getText().toString().trim();
        String phone      = etPhone.getText().toString().trim();
        String email      = etEmail.getText().toString().trim();
        String blood      = etBloodType.getText().toString().trim();
        String allergies  = etAllergies.getText().toString().trim();
        String conditions = etConditions.getText().toString().trim();
        String meds       = etMedications.getText().toString().trim();

        if (fullname.isEmpty() || phone.isEmpty()) {
            Toast.makeText(getContext(), "Name and phone are required!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Local DB như cũ (nếu bạn dùng)
        com.example.womensafetyapp.database.ContactDatabaseHelper dbLocal =
                new com.example.womensafetyapp.database.ContactDatabaseHelper(requireContext());
        com.example.womensafetyapp.models.UserProfile p = new com.example.womensafetyapp.models.UserProfile();
        p.setFullName(fullname);
        p.setPhone(phone);
        p.setEmail(email);
        p.setBloodType(blood);
        p.setAllergies(allergies);
        p.setConditions(conditions);
        p.setMedications(meds);
        dbLocal.upsertProfile(p);

        // Greeting ở Home
        android.content.SharedPreferences prefs =
                requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE);
        prefs.edit().putString("fullname", fullname).apply();

        // Firebase: CHỈ ghi /users/{uid}/profile (không đụng emergency_contacts)
        if (NetworkUtils.isNetworkAvailable(getContext())) {
            com.google.firebase.database.DatabaseReference ref =
                    com.google.firebase.database.FirebaseDatabase.getInstance()
                            .getReference("users")
                            .child(currentUid)
                            .child("profile");

            // dùng setValue(p) vì đã map bằng @PropertyName (ra đúng full_name/blood_type)
            ref.setValue(p);
        }

        Toast.makeText(getContext(), "Saved successfully!", Toast.LENGTH_SHORT).show();

        // Điều hướng sang trang chi tiết
        Fragment detailFragment = new ProfileDetailFragment();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit();
    }

}
