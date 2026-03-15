package com.example.womensafetyapp.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.womensafetyapp.R;
import com.example.womensafetyapp.adapters.ContactAdapter;
import com.example.womensafetyapp.database.ContactDatabaseHelper;
import com.example.womensafetyapp.models.EmergencyContact;
import com.example.womensafetyapp.utils.NetworkUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class ContactListFragment extends Fragment {

    private static final int MAX_CONTACTS = 3;

    private final ArrayList<EmergencyContact> contacts = new ArrayList<>();
    private ContactAdapter adapter;
    private RecyclerView rv;
    private Button addBtn;

    private ContactDatabaseHelper db;
    private DatabaseReference ref;

    private int autoId = 1000;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_contact_list, container, false);

        Toolbar toolbar = v.findViewById(R.id.toolbarContacts);
        toolbar.setNavigationOnClickListener(view ->
                requireActivity().getSupportFragmentManager().popBackStack()
        );

        db = new ContactDatabaseHelper(requireContext());

        // UID hiện tại
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        rv = v.findViewById(R.id.rvContacts);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ContactAdapter(contacts, (item, position) -> {
            if (position < 0 || position >= contacts.size()) return;

            // Xoá trên Firebase trước (nếu có key)
            if (ref != null && item.getFirebaseKey() != null && !item.getFirebaseKey().isEmpty()) {
                ref.child(item.getFirebaseKey()).removeValue();
            }

            // Xoá local và reload
            db.deleteContact(item.getContact_id());
            loadFromLocal();
            updateAddButtonState();
        });
        rv.setAdapter(adapter);

        // Luôn load local trước (để lên UI tức thì)
        loadFromLocal();

        // Nếu đã đăng nhập thì trỏ đúng path Firebase
        if (uid != null) {
            ref = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(uid)
                    .child("emergency_contacts");

            // 🔁 Đồng bộ: thay thế toàn bộ local bằng dữ liệu từ Firebase
            syncFromFirebaseReplaceLocal();
        }

        // Nút thêm
        addBtn = v.findViewById(R.id.btnAdd_list);
        addBtn.setOnClickListener(view -> {
            if (contacts.size() >= MAX_CONTACTS) {
                showLimitReached();
                return;
            }
            showAddContactDialog();
        });

        // Nếu chưa đăng nhập thì khoá thêm
        if (uid == null) {
            addBtn.setEnabled(false);
            addBtn.setAlpha(0.5f);
        }

        updateAddButtonState();
        return v;
    }

    /** Đọc lại toàn bộ từ SQLite vào adapter */
    private void loadFromLocal() {
        contacts.clear();
        contacts.addAll(db.getAllContacts());
        adapter.notifyDataSetChanged();

        for (EmergencyContact c : contacts) {
            autoId = Math.max(autoId, c.getContact_id());
        }
        updateAddButtonState();
    }

    /** Đồng bộ kiểu "Firebase là nguồn sự thật" → xoá sạch local & nạp lại */
    private void syncFromFirebaseReplaceLocal() {
        if (ref == null || !NetworkUtils.isNetworkAvailable(requireContext())) return;

        ref.get().addOnSuccessListener(snapshot -> {
            // Xoá sạch bảng để tránh mọi loại trùng
            db.wipeContacts();

            int added = 0;
            for (DataSnapshot snap : snapshot.getChildren()) {
                if (added >= MAX_CONTACTS) break;

                String key = snap.getKey();
                String name = snap.child("name").getValue(String.class);
                String phone = snap.child("phone").getValue(String.class);
                String relation = snap.child("relation").getValue(String.class);

                if (key == null) continue;
                if ((name == null || name.trim().isEmpty()) &&
                        (phone == null || phone.trim().isEmpty())) continue;

                EmergencyContact c = new EmergencyContact(++autoId, 1, name, phone, relation);
                c.setFirebaseKey(key);
                db.addContact(c);
                added++;
            }

            // Sau khi DB chuẩn → hiển thị lại từ local
            loadFromLocal();
        });
    }

    private void showAddContactDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_contact, null, false);

        EditText etName     = dialogView.findViewById(R.id.edtContactName);
        EditText etPhone    = dialogView.findViewById(R.id.edtContactPhone);
        EditText etRelation = dialogView.findViewById(R.id.edtContactRelation);
        Button btnAdd       = dialogView.findViewById(R.id.btnAdd);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        btnAdd.setOnClickListener(v -> {
            if (contacts.size() >= MAX_CONTACTS) {
                dialog.dismiss();
                showLimitReached();
                return;
            }

            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String relation = etRelation.getText().toString().trim();

            if (name.isEmpty())  { etName.setError("Nhập tên");  return; }
            if (phone.isEmpty()) { etPhone.setError("Nhập số");  return; }

            // Lưu local trước để có UI tức thì
            EmergencyContact item = new EmergencyContact(++autoId, 1, name, phone, relation);
            long rowId = db.addContact(item);
            item.setContact_id((int) rowId);
            loadFromLocal();

            // Nếu online + có ref: đẩy lên Firebase rồi reload lại từ DB sau khi có key
            if (ref != null && NetworkUtils.isNetworkAvailable(requireContext())) {
                DatabaseReference newRef = ref.push();
                String key = newRef.getKey();

                java.util.HashMap<String, Object> map = new java.util.HashMap<>();
                map.put("name", name);
                map.put("phone", phone);
                map.put("relation", relation);

                newRef.setValue(map)
                        .addOnSuccessListener(unused -> {
                            db.updateFirebaseKey(item.getContact_id(), key);
                            // Sau khi có key → đồng bộ lại toàn bộ để 100% không trùng
                            syncFromFirebaseReplaceLocal();
                        });
            }

            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateAddButtonState() {
        if (addBtn == null) return;
        boolean canAdd = contacts.size() < MAX_CONTACTS;
        addBtn.setEnabled(canAdd);
        addBtn.setAlpha(canAdd ? 1f : 0.5f);
    }

    private void showLimitReached() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Giới hạn")
                .setMessage("Bạn chỉ có thể lưu tối đa " + MAX_CONTACTS + " liên hệ khẩn cấp.")
                .setPositiveButton("OK", null)
                .show();
    }
}
