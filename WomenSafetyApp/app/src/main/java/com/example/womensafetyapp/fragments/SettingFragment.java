package com.example.womensafetyapp.fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.womensafetyapp.R;
import com.example.womensafetyapp.activities.MainActivity;
import com.example.womensafetyapp.services.SosService;
import com.example.womensafetyapp.utils.SosPrefs;

public class SettingFragment extends Fragment {

    private Switch swShake, swVoice;
    // ✅ Added: Auto Call switch
    private Switch swAutoCall; // ✅ Added
    // ✅ ADDED: Panic Mode switch
    private Switch swPanicMode; // ✅ Added

    // Xin quyền micro cho SOS Voice
    private final ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    if (swVoice != null) swVoice.setChecked(false);
                    SosPrefs.setVoiceEnabled(requireContext(), false);
                    Toast.makeText(requireContext(), "Bạn đã từ chối quyền micro.", Toast.LENGTH_SHORT).show();
                }
                maybeStartOrStopService();
            });

    // Android 13+: xin POST_NOTIFICATIONS (không bắt buộc nhưng nên có)
    private final ActivityResultLauncher<String> notifPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // Bất kể user có cấp hay không, ta vẫn tiếp tục start/stop service
                maybeStartOrStopService();
            });

    // ✅ Added: xin quyền CALL_PHONE cho Auto Call (nếu bạn gọi bằng ACTION_CALL)
    private final ActivityResultLauncher<String> callPermissionLauncher = // ✅ Added
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    if (swAutoCall != null) swAutoCall.setChecked(false);
                    SosPrefs.setAutoCallEnabled(requireContext(), false);
                    Toast.makeText(requireContext(), "Bạn đã từ chối quyền gọi điện.", Toast.LENGTH_SHORT).show();
                }
                // Không cần start/stop service vì Auto Call không chạy nền
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_setting, container, false);

        // Views
        swShake = view.findViewById(R.id.switch1);
        swVoice = view.findViewById(R.id.switch2);
        // ✅ Added: tham chiếu tới công tắc Auto Call
        swAutoCall = view.findViewById(R.id.switchAutoCall); // ✅ Added
        // ✅ ADDED: tham chiếu tới Panic Mode
        swPanicMode = view.findViewById(R.id.switchPanicMode); // ✅ Added
        Button btnLogout = view.findViewById(R.id.btnLogout);

        // Khởi tạo trạng thái công tắc theo prefs
        swShake.setChecked(SosPrefs.isShakeEnabled(requireContext()));
        swVoice.setChecked(SosPrefs.isVoiceEnabled(requireContext()));
        // ✅ Added: init trạng thái Auto Call
        if (swAutoCall != null) { // phòng lỗi nếu layout chưa thêm
            swAutoCall.setChecked(SosPrefs.isAutoCallEnabled(requireContext()));
        }
        // ✅ ADDED: init trạng thái Panic Mode
        if (swPanicMode != null) {
            swPanicMode.setChecked(SosPrefs.isPanicModeEnabled(requireContext()));
        }

        // Listeners
        swShake.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SosPrefs.setShakeEnabled(requireContext(), isChecked);
            maybeStartOrStopService();
        });

        swVoice.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // cần quyền micro
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    // tắt lại, chờ user cấp rồi mới bật
                    swVoice.setChecked(false);
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                    return;
                }
            }
            SosPrefs.setVoiceEnabled(requireContext(), isChecked);
            maybeStartOrStopService();
        });

        // ✅ Added: Listener cho Auto Call (không đụng gì đến service)
        if (swAutoCall != null) {
            swAutoCall.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // Nếu bạn định dùng ACTION_CALL, cần quyền CALL_PHONE.
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE)
                            != PackageManager.PERMISSION_GRANTED) {
                        // Tắt lại, xin quyền trước
                        swAutoCall.setChecked(false);
                        callPermissionLauncher.launch(Manifest.permission.CALL_PHONE);
                        return;
                    }
                }
                SosPrefs.setAutoCallEnabled(requireContext(), isChecked);
                // Không gọi maybeStartOrStopService(); Auto Call chỉ áp dụng khi SOS thực thi
                Toast.makeText(requireContext(),
                        isChecked ? "Auto Call enabled" : "Auto Call disabled",
                        Toast.LENGTH_SHORT).show();
            });
        }

        // ✅ ADDED: Listener cho Panic Mode (chỉ lưu trạng thái)
        if (swPanicMode != null) {
            swPanicMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                SosPrefs.setPanicModeEnabled(requireContext(), isChecked);
                Toast.makeText(requireContext(),
                        isChecked ? "Panic Mode enabled" : "Panic Mode disabled",
                        Toast.LENGTH_SHORT).show();
                // Không start/stop service: Panic Mode chỉ ảnh hưởng hành vi nút SOS ở Home
            });
        }

        // Logout
        btnLogout.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).logoutUser();
            }
        });

        // Android 13+: xin thông báo để foreground notification hiển thị đầy đủ
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        return view;
    }

    // Thêm ở đầu class
    private final ActivityResultLauncher<String> locPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // Sau khi user chọn, thử start/stop lại
                maybeStartOrStopService();
            });

    private boolean hasFineOrCoarse() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasMic() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ✅ Added: tiện kiểm tra quyền gọi (nếu cần dùng chỗ khác)
    private boolean hasCallPermission() { // ✅ Added
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void maybeStartOrStopService() {
        Context ctx = requireContext();
        boolean wantShake = SosPrefs.isShakeEnabled(ctx);
        boolean wantVoice = SosPrefs.isVoiceEnabled(ctx);
        boolean needService = wantShake || wantVoice;

        // Nếu service có type=location và bạn sẽ lấy vị trí khi SOS,
        // hãy đòi quyền location trước khi start để tránh SecurityException
        if (needService && !hasFineOrCoarse()) {
            // gợi ý xin FINE, nếu từ chối có thể fallback COARSE
            locPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        if (wantVoice && !hasMic()) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }

        Intent svc = new Intent(ctx, SosService.class);
        if (needService) {
            ContextCompat.startForegroundService(ctx, svc);
        } else {
            ctx.stopService(svc);
        }
    }
}
