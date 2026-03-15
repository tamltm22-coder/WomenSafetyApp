package com.example.womensafetyapp.activities;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.womensafetyapp.R;
import com.example.womensafetyapp.database.ContactDatabaseHelper;
import com.example.womensafetyapp.models.EmergencyContact;
import com.google.android.material.appbar.MaterialToolbar;

// ✅ Added: dùng trạng thái Auto Call từ Settings
import com.example.womensafetyapp.utils.SosPrefs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn Gọi Khẩn Cấp:
 * - Hiển thị Emergency Contacts (SQLite) → bấm để gọi.
 * - Hiển thị 112/113/115 mỗi dòng.
 * - Phần "Số điện thoại địa phương": thêm/xoá lưu vĩnh viễn (SharedPreferences).
 * - Auto-call: sau 30s không thao tác, tự gọi 2 liên hệ khẩn cấp đầu tiên; dừng khi có người nghe máy.
 */
public class CallButtonSosActivity extends AppCompatActivity {

    // ====== IDs trong layout XML ======
    private LinearLayout layoutContacts;     // danh sách EmergencyContact
    private Button btn112, btn113, btn115;

    // ====== Auto-call sau 30s ======
    private static final int REQ_CALL = 501;
    private static final int REQ_READ_PHONE_STATE = 502;
    private final Handler autoHandler = new Handler(Looper.getMainLooper());
    private final long AUTO_DELAY_MS = 30_000L; // 30s
    private final Runnable autoRunnable = this::startAutoCallTwoContacts;

    // Trạng thái auto-call tuần tự
    private final List<String> callQueue = new ArrayList<>();
    private int currentIndex = -1;
    private boolean dialingInProgress = false;
    private boolean everOffhookThisCall = false;
    private String lastNumber = "";

    private TelephonyManager telephonyManager;
    private final Handler callHandler = new Handler(Looper.getMainLooper());

    // ====== Local numbers (SharedPreferences) ======
    private static final String PREFS = "sos_prefs";
    private static final String KEY_LOCAL_NUMBERS = "local_numbers_json";

    private LinearLayout localSectionContainer; // container riêng cho "Số điện thoại địa phương"
    private LinearLayout localListContainer;    // danh sách nút local
    private Button btnAddLocal;                 // nút thêm số địa phương

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.call_button_sos);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        layoutContacts = findViewById(R.id.layoutContacts);
        btn112 = findViewById(R.id.btn112);
        btn113 = findViewById(R.id.btn113);
        btn115 = findViewById(R.id.btn115);

        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        // Các số khẩn cấp quốc gia
        if (btn112 != null) btn112.setOnClickListener(v -> { cancelAutoTimer(); callSingle("112"); });
        if (btn113 != null) btn113.setOnClickListener(v -> { cancelAutoTimer(); callSingle("113"); });
        if (btn115 != null) btn115.setOnClickListener(v -> { cancelAutoTimer(); callSingle("115"); });

        // Render Emergency Contacts động từ SQLite
        buildEmergencyContactButtons();

        // Tạo & render phần "Số điện thoại địa phương"
        ensureLocalNumbersSection();
        buildLocalNumbersUI();

        // Bất kỳ thao tác chạm nào trong màn này coi như có tương tác → reset auto timer
        View root = findViewById(android.R.id.content);
        if (root != null) {
            root.setOnTouchListener((v, e) -> {
                // ✅ Modified: chỉ reset timer nếu Auto Call đang bật
                if (SosPrefs.isAutoCallEnabled(this)) {
                    scheduleAutoTimer(); // chạm là reset đếm lại 30s
                }
                return false;
            });
        }

        // (Tuỳ chọn) Nếu muốn gọi ngay khi mở màn & Auto Call bật:
        // if (SosPrefs.isAutoCallEnabled(this)) startAutoCallTwoContacts(); // ✅ Optional
    }

    @Override
    protected void onResume() {
        super.onResume();
        // ✅ Modified: chỉ đếm 30s nếu Auto Call đang bật, ngược lại hủy timer
        if (SosPrefs.isAutoCallEnabled(this)) {
            scheduleAutoTimer();
        } else {
            cancelAutoTimer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelAutoTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelAutoTimer();
        unregisterPhoneListener();
    }

    // ===================== EMERGENCY CONTACTS (SQLite) =====================

    /** Tạo các button theo số lượng liên hệ đã lưu trong SQLite. */
    private void buildEmergencyContactButtons() {
        if (layoutContacts == null) return;
        layoutContacts.removeAllViews();

        ContactDatabaseHelper db = new ContactDatabaseHelper(this);
        ArrayList<EmergencyContact> contacts = db.getAllContacts();

        if (contacts == null || contacts.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("Chưa có liên hệ khẩn cấp. Hãy thêm trong mục Emergency Contact.");
            tv.setTextColor(Color.parseColor("#666666"));
            tv.setTextSize(14);
            tv.setPadding(0, 0, 0, dp(12));
            layoutContacts.addView(tv);
            return;
        }

        for (EmergencyContact c : contacts) {
            String phone = safe(c.getPhone());
            String name  = safe(c.getName());
            if (phone.isEmpty()) continue;

            Button btn = makeWhiteButton((name.isEmpty() ? "Liên hệ" : name) + " - " + phone);
            btn.setOnClickListener(v -> { cancelAutoTimer(); callSingle(phone); });
            layoutContacts.addView(btn);
        }
    }

    // ===================== LOCAL NUMBERS (SharedPreferences) =====================

    /** Tạo phần "Số điện thoại địa phương" phía dưới Emergency Contact (bằng code, không cần sửa XML). */
    private void ensureLocalNumbersSection() {
        if (localSectionContainer != null) return; // đã tạo

        // Lấy parent lớn nhất của layoutContacts (LinearLayout nội dung bên trong ScrollView)
        ViewGroup parent = (ViewGroup) layoutContacts.getParent();
        if (parent == null) parent = (ViewGroup) findViewById(android.R.id.content);

        // Tiêu đề
        TextView title = new TextView(this);
        title.setText("Số điện thoại địa phương:");
        title.setTextColor(Color.parseColor("#555555"));
        title.setTextSize(15);
        title.setPadding(0, dp(16), 0, dp(8));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        parent.addView(title);

        // Container phần local
        localSectionContainer = new LinearLayout(this);
        localSectionContainer.setOrientation(LinearLayout.VERTICAL);
        parent.addView(localSectionContainer);

        // Danh sách local
        localListContainer = new LinearLayout(this);
        localListContainer.setOrientation(LinearLayout.VERTICAL);
        localSectionContainer.addView(localListContainer);

        // Nút thêm
        btnAddLocal = new Button(this);
        btnAddLocal.setText("➕ Thêm số điện thoại địa phương");
        btnAddLocal.setAllCaps(false);
        btnAddLocal.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF6A73")));
        btnAddLocal.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        btnAddLocal.setLayoutParams(lp);
        btnAddLocal.setOnClickListener(v -> { cancelAutoTimer(); showAddLocalDialog(); });
        localSectionContainer.addView(btnAddLocal);
    }

    /** Vẽ lại danh sách nút local từ SharedPreferences. */
    private void buildLocalNumbersUI() {
        if (localListContainer == null) return;
        localListContainer.removeAllViews();

        List<LocalNumber> locals = loadLocalNumbers();
        if (locals.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("Chưa có số địa phương nào. Hãy nhấn nút \"Thêm số điện thoại địa phương\".");
            tv.setTextColor(Color.parseColor("#777777"));
            tv.setTextSize(14);
            tv.setPadding(0, 0, 0, dp(8));
            localListContainer.addView(tv);
            return;
        }

        for (int i = 0; i < locals.size(); i++) {
            LocalNumber ln = locals.get(i);
            String title = (safe(ln.name).isEmpty() ? "Số địa phương" : ln.name) + " - " + ln.phone;
            Button btn = makeWhiteButton(title);
            final int index = i;
            btn.setOnClickListener(v -> { cancelAutoTimer(); callSingle(ln.phone); });
            btn.setOnLongClickListener(v -> {
                confirmDeleteLocal(index, ln);
                return true;
            });
            localListContainer.addView(btn);
        }
    }

    /** Dialog thêm số địa phương (tên tuỳ chọn, số bắt buộc). */
    private void showAddLocalDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);

        EditText edtName = new EditText(this);
        edtName.setHint("Tên (tuỳ chọn)");
        edtName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        form.addView(edtName);

        View spacer = new View(this);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        spacer.setLayoutParams(spLp);
        form.addView(spacer);

        EditText edtPhone = new EditText(this);
        edtPhone.setHint("Số điện thoại (bắt buộc)");
        edtPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        form.addView(edtPhone);

        new AlertDialog.Builder(this)
                .setTitle("Thêm số điện thoại địa phương")
                .setView(form)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String name = safe(edtName.getText() == null ? "" : edtName.getText().toString());
                    String phone = safe(edtPhone.getText() == null ? "" : edtPhone.getText().toString());

                    if (phone.isEmpty()) {
                        Toast.makeText(this, "Vui lòng nhập số điện thoại.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!looksLikePhone(phone)) {
                        Toast.makeText(this, "Số điện thoại không hợp lệ.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<LocalNumber> list = loadLocalNumbers();
                    list.add(new LocalNumber(name, phone));
                    saveLocalNumbers(list);
                    buildLocalNumbersUI();
                    Toast.makeText(this, "Đã thêm số địa phương.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /** Xác nhận xoá một số địa phương (nhấn giữ). */
    private void confirmDeleteLocal(int index, LocalNumber ln) {
        new AlertDialog.Builder(this)
                .setTitle("Xoá số địa phương")
                .setMessage("Bạn có chắc muốn xoá \"" +
                        (safe(ln.name).isEmpty() ? ln.phone : ln.name + " - " + ln.phone) + "\"?")
                .setPositiveButton("Xoá", (dialog, which) -> {
                    List<LocalNumber> list = loadLocalNumbers();
                    if (index >= 0 && index < list.size()) {
                        list.remove(index);
                        saveLocalNumbers(list);
                        buildLocalNumbersUI();
                        Toast.makeText(this, "Đã xoá.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ===================== GỌI ĐIỆN =====================

    /** Gọi một số: dùng ACTION_CALL (gọi thẳng). Đổi sang ACTION_DIAL nếu chỉ muốn mở quay số. */
    private void callSingle(String number) {
        if (!ensurePermission(Manifest.permission.CALL_PHONE, REQ_CALL)) return;
        startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number)));
    }

    // ====== Auto-call sau 30s nếu không chọn số ======
    private void scheduleAutoTimer() {
        cancelAutoTimer();
        autoHandler.postDelayed(autoRunnable, AUTO_DELAY_MS);
    }

    private void cancelAutoTimer() {
        autoHandler.removeCallbacks(autoRunnable);
    }

    /** Lấy 2 số EmergencyContact đầu tiên và gọi tuần tự cho đến khi có người nghe máy. */
    private void startAutoCallTwoContacts() {
        // ✅ Added: guard – chỉ chạy khi Auto Call bật
        if (!SosPrefs.isAutoCallEnabled(this)) return;

        // Tạo queue từ 2 liên hệ đầu tiên
        callQueue.clear();
        ContactDatabaseHelper db = new ContactDatabaseHelper(this);
        ArrayList<EmergencyContact> contacts = db.getAllContacts();
        int added = 0;
        for (EmergencyContact c : contacts) {
            String p = safe(c.getPhone());
            if (!p.isEmpty()) {
                callQueue.add(p);
                if (++added == 2) break;
            }
        }

        if (callQueue.isEmpty()) {
            Toast.makeText(this, "Không có liên hệ để gọi tự động.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Không thao tác trong 30s. Bắt đầu gọi liên hệ khẩn cấp…", Toast.LENGTH_LONG).show();

        if (!ensurePermission(Manifest.permission.CALL_PHONE, REQ_CALL)
                || !ensurePermission(Manifest.permission.READ_PHONE_STATE, REQ_READ_PHONE_STATE)) {
            return;
        }

        registerPhoneListener();
        currentIndex = -1;
        callNextInQueue();
    }

    private void callNextInQueue() {
        currentIndex++;
        if (currentIndex >= callQueue.size()) {
            Toast.makeText(this, "Đã thử gọi 2 liên hệ nhưng không ai nghe.", Toast.LENGTH_LONG).show();
            stopAutoCall();
            return;
        }

        lastNumber = callQueue.get(currentIndex);
        everOffhookThisCall = false;
        dialingInProgress = true;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
            dialingInProgress = false;
            return;
        }
        startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + lastNumber)));
        Toast.makeText(this, "Đang gọi: " + lastNumber, Toast.LENGTH_SHORT).show();
    }

    private void stopAutoCall() {
        dialingInProgress = false;
        everOffhookThisCall = false;
        currentIndex = -1;
        lastNumber = "";
        unregisterPhoneListener();
    }

    private final PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    // Có người nhấc máy
                    everOffhookThisCall = true;
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    // Cuộc gọi kết thúc
                    if (dialingInProgress) {
                        dialingInProgress = false;
                        if (everOffhookThisCall) {
                            Toast.makeText(CallButtonSosActivity.this,
                                    "Đã có người nghe máy: " + lastNumber, Toast.LENGTH_LONG).show();
                            stopAutoCall();
                        } else {
                            // Không ai nghe → gọi số kế tiếp (nếu còn)
                            callHandler.postDelayed(() -> callNextInQueue(), 1200);
                        }
                    }
                    break;
                case TelephonyManager.CALL_STATE_RINGING:
                default:
                    break;
            }
        }
    };

    private void registerPhoneListener() {
        try {
            if (telephonyManager != null) {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            }
        } catch (SecurityException ignored) {}
    }

    private void unregisterPhoneListener() {
        try {
            if (telephonyManager != null) {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            }
        } catch (Exception ignored) {}
    }

    // ===================== Utils =====================

    private Button makeWhiteButton(String text) {
        Button btn = new Button(this);
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) btn.getLayoutParams();
        lp.bottomMargin = dp(10);
        btn.setLayoutParams(lp);

        btn.setAllCaps(false);
        btn.setText(text);

        // style nút trắng
        btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFFFFF")));
        btn.setTextColor(Color.parseColor("#333333"));
        btn.setPadding(dp(12), dp(12), dp(12), dp(12));
        btn.setStateListAnimator(null);
        btn.setElevation(dp(2));
        return btn;
    }

    private boolean ensurePermission(String perm, int req) {
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{perm}, req);
            return false;
        }
        return true;
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    private boolean looksLikePhone(String p) {
        // Kiểm tra cơ bản: 8–15 ký tự số, cho phép + ở đầu
        return p.matches("^\\+?[0-9]{8,15}$");
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    // ===================== Local numbers persistence (SharedPreferences + JSON) =====================

    private static class LocalNumber {
        String name;
        String phone;
        LocalNumber(String n, String p) { this.name = n; this.phone = p; }
    }

    private List<LocalNumber> loadLocalNumbers() {
        ArrayList<LocalNumber> out = new ArrayList<>();
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String json = sp.getString(KEY_LOCAL_NUMBERS, "");
        if (json == null || json.trim().isEmpty()) return out;

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String name = o.optString("name", "");
                String phone = o.optString("phone", "");
                if (phone != null && !phone.trim().isEmpty()) {
                    out.add(new LocalNumber(name, phone));
                }
            }
        } catch (JSONException e) {
            // nếu lỗi parse → bỏ qua
        }
        return out;
    }

    private void saveLocalNumbers(List<LocalNumber> list) {
        JSONArray arr = new JSONArray();
        for (LocalNumber ln : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("name", safe(ln.name));
                o.put("phone", safe(ln.phone));
            } catch (JSONException ignored) {}
            arr.put(o);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_LOCAL_NUMBERS, arr.toString())
                .apply();
    }
}
