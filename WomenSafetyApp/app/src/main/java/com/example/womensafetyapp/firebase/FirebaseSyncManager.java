package com.example.womensafetyapp.firebase;

import android.util.Log;
import androidx.annotation.NonNull;

import com.example.womensafetyapp.dao.MedicalInfoDao;
import com.example.womensafetyapp.dao.UserDao;
import com.example.womensafetyapp.database.ContactDatabaseHelper;
import com.example.womensafetyapp.models.EmergencyContact;
import com.example.womensafetyapp.models.MedicalInfo;
import com.example.womensafetyapp.models.User;
import com.example.womensafetyapp.models.UserProfile;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;

public class FirebaseSyncManager {

    private final DatabaseReference dbRef = FirebaseManager.getInstance().getRootRef();

    // 🔹 Đồng bộ thông tin người dùng
    public void syncUser(User user) {
        if (user == null || user.getUid() == null) {
            Log.e("FirebaseSync", "⚠️ User or UID is null");
            return;
        }

        String uid = user.getUid();
        Log.d("FirebaseSync", "Pushing user: " + uid);

        dbRef.child("users").child(uid)
                .setValue(user, (error, ref) -> {
                    if (error == null)
                        Log.d("FirebaseSync", "✅ User synced successfully");
                    else
                        Log.e("FirebaseSync", "❌ Failed to sync user", error.toException());
                });
    }

    // 🔹 Đồng bộ thông tin y tế
    public void syncMedicalInfo(MedicalInfo info) {
        if (info == null || info.getUid() == null) {
            Log.e("FirebaseSync", "⚠️ MedicalInfo or UID is null");
            return;
        }

        String uid = info.getUid();
        Log.d("FirebaseSync", "Pushing medical info for user: " + uid);

        dbRef.child("medical_info").child(uid)
                .setValue(info, (error, ref) -> {
                    if (error == null)
                        Log.d("FirebaseSync", "✅ Medical info synced successfully");
                    else
                        Log.e("FirebaseSync", "❌ Failed to sync medical info", error.toException());
                });
    }

    // 🔹 Tải dữ liệu từ Firebase về (nếu có)
    public void fetchUserData(String uid, UserDao userDao, MedicalInfoDao medicalInfoDao) {
        Log.d("FirebaseSync", "Fetching data for UID: " + uid);

        dbRef.child("users").child(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        User user = snapshot.getValue(User.class);
                        if (user != null) userDao.insertOrUpdate(user);
                    }
                })
                .addOnFailureListener(e -> Log.e("FirebaseSync", "❌ Failed to fetch user data", e));

        dbRef.child("medical_info").child(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        MedicalInfo info = snapshot.getValue(MedicalInfo.class);
                        if (info != null) medicalInfoDao.insertOrUpdate(info);
                    }
                })
                .addOnFailureListener(e -> Log.e("FirebaseSync", "❌ Failed to fetch medical info", e));
    }
    // 🔹 Lưu Emergency Contact theo UID
    public void syncEmergencyContact(String uid, EmergencyContact contact) {
        if (uid == null || contact == null) return;

        DatabaseReference ref = dbRef.child("users").child(uid).child("emergency_contacts");

        if (contact.getFirebaseKey() == null || contact.getFirebaseKey().isEmpty()) {
            // Tạo key mới nếu chưa có
            String key = ref.push().getKey();
            contact.setFirebaseKey(key);
        }

        ref.child(contact.getFirebaseKey()).setValue(contact, (error, dbRef1) -> {
            if (error == null)
                Log.d("FirebaseSync", "✅ Synced contact: " + contact.getName());
            else
                Log.e("FirebaseSync", "❌ Failed to sync contact", error.toException());
        });
    }

    // 🔹 Tải toàn bộ Emergency Contacts của tài khoản hiện tại
    public void fetchEmergencyContacts(String uid, ContactDatabaseHelper localDb) {
        if (uid == null) return;

        dbRef.child("users").child(uid).child("emergency_contacts")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) return;

                    for (DataSnapshot child : snapshot.getChildren()) {
                        EmergencyContact contact = child.getValue(EmergencyContact.class);
                        if (contact == null) continue;

                        // Nếu chưa có trong local DB thì thêm
                        if (!localDb.existsByFirebaseKey(contact.getFirebaseKey())) {
                            localDb.addContact(contact);
                        }
                    }
                    Log.d("FirebaseSync", "✅ Fetched contacts for uid=" + uid);
                })
                .addOnFailureListener(e -> Log.e("FirebaseSync", "❌ Fetch contacts failed", e));
    }
    public void fetchProfileToLocal(String uid, ContactDatabaseHelper localDb) {
        if (uid == null) return;

        DatabaseReference userRef = dbRef.child("users").child(uid);              // users/{uid} :contentReference[oaicite:1]{index=1}
        DatabaseReference medRef = dbRef.child("medical_info").child(uid);       // medical_info/{uid} :contentReference[oaicite:2]{index=2}

        final String[] fullNameBox = {null};
        final String[] phoneBox = {null};
        final String[] emailBox = {null};

        userRef.get().addOnSuccessListener(userSnap -> {
            // Lấy các key phổ biến, có gì thì lấy nấy (tránh lệch tên field)
            fullNameBox[0] = firstString(userSnap, "fullName", "fullname", "name");
            phoneBox[0] = firstString(userSnap, "phone", "phoneNumber");
            emailBox[0] = firstString(userSnap, "email");

            medRef.get().addOnSuccessListener(medSnap -> {
                String blood = firstString(medSnap, "bloodType", "blood");
                String allergies = firstString(medSnap, "allergies");
                String conditions = firstString(medSnap, "conditions");
                String medications = firstString(medSnap, "medications");

                UserProfile p = new UserProfile();
                p.setFullName(fullNameBox[0]);
                p.setPhone(phoneBox[0]);
                p.setEmail(emailBox[0]);
                p.setBloodType(blood);
                p.setAllergies(allergies);
                p.setConditions(conditions);
                p.setMedications(medications);

                localDb.upsertProfile(p);  // ghi vào bảng profile local :contentReference[oaicite:3]{index=3}
                Log.d("FirebaseSync", "✅ Profile fetched & upserted for uid=" + uid);
            }).addOnFailureListener(e ->
                    Log.e("FirebaseSync", "❌ Fetch medical_info failed", e)
            );
        }).addOnFailureListener(e ->
                Log.e("FirebaseSync", "❌ Fetch users failed", e)
        );
    }
    private String firstString(DataSnapshot snap, String... keys) {
        if (snap == null) return null;
        for (String k : keys) {
            Object v = snap.child(k).getValue();
            if (v != null) return String.valueOf(v);
        }
        return null;
    }
}
