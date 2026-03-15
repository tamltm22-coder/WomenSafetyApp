    package com.example.womensafetyapp.fragments;

    import android.content.Intent;
    import android.os.Bundle;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.view.ViewGroup;
    import android.widget.ImageView;
    import android.widget.TextView;
    import android.widget.Toast;
    import com.example.womensafetyapp.utils.SosPrefs;
    import com.example.womensafetyapp.utils.SosHelper; // nếu mày đang dùng helper gửi SMS
    import androidx.annotation.NonNull;
    import androidx.annotation.Nullable;
    import androidx.appcompat.app.AlertDialog;
    import androidx.cardview.widget.CardView;
    import androidx.fragment.app.Fragment;

    import com.example.womensafetyapp.R;
    import com.example.womensafetyapp.activities.LiveTrackingActivity;
    import com.google.firebase.auth.FirebaseAuth;
    import com.google.firebase.auth.FirebaseUser;

    public class HomeFragment extends Fragment {

        private TextView tvAppSubtitle;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater,
                                 @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {

            View view = inflater.inflate(R.layout.fragment_home, container, false);

            tvAppSubtitle = view.findViewById(R.id.tvAppSubtitle);

            // 🧭 Mở ProfileFragment
            CardView cardProfile = view.findViewById(R.id.cardProfile);
            cardProfile.setOnClickListener(v -> {
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new ProfileDetailFragment())
                        .addToBackStack(null)
                        .commit();
            });

            // 📞 Mở ContactListFragment
            ImageView ivEmergencyContact = view.findViewById(R.id.ivEmergencyContact);
            TextView tvEmergencyContact = view.findViewById(R.id.tvEmergencyContact);

            View.OnClickListener openContactList = v -> {
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new ContactListFragment())
                        .addToBackStack(null)
                        .commit();
            };

            ivEmergencyContact.setOnClickListener(openContactList);
            tvEmergencyContact.setOnClickListener(openContactList);


            View cardSOS = view.findViewById(R.id.cardSOS);
            cardSOS.setOnClickListener(v -> {
                if (SosPrefs.isPanicModeEnabled(requireContext())) {
                    // 👉 Panic Mode: mở SosFragment và auto gửi Quick SMS
                    Bundle args = new Bundle();
                    args.putBoolean("autoQuickSms", true);

                    SosFragment frag = new SosFragment();
                    frag.setArguments(args);

                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .setReorderingAllowed(true)
                            .replace(R.id.fragment_container, frag)
                            .addToBackStack("sos")
                            .commit();
                } else {
                    // 👉 Bình thường: mở SosFragment như cũ
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .setReorderingAllowed(true)
                            .replace(R.id.fragment_container, new SosFragment())
                            .addToBackStack("sos")
                            .commit();
                }
            });

            // 👆 Giữ long click autoStartTracking (mở SosFragment và bật tracking tự động)
            cardSOS.setOnLongClickListener(v -> {
                Bundle args = new Bundle();
                args.putBoolean("autoStartTracking", true);

                SosFragment frag = new SosFragment();
                frag.setArguments(args);

                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.fragment_container, frag)
                        .addToBackStack("sos")
                        .commit();

                return true;
            });

            // 🗺️ Victim Location: tự lấy UID hiện tại và mở LiveTrackingActivity
            CardView cardVictim = view.findViewById(R.id.cardVictimLocation);
            if (cardVictim != null) {
                cardVictim.setOnClickListener(v -> {
                    FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                    if (u == null) {
                        Toast.makeText(requireContext(), "Bạn chưa đăng nhập.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String uid = u.getUid();
                    Intent i = new Intent(requireContext(), LiveTrackingActivity.class);
                    // Có thể không cần extra nếu Activity tự lấy current UID; nhưng truyền vào cho chắc:
                    i.putExtra("targetUid", uid);
                    startActivity(i);
                });
            }

            applyGreeting();
            return view;
        }

        @Override
        public void onResume() {
            super.onResume();
            applyGreeting();
        }

        private void applyGreeting() {
            if (tvAppSubtitle == null) return;
            String name = readFullname();
            if (name != null && !name.trim().isEmpty()) {
                tvAppSubtitle.setText("Hi " + name);
            } else {
                tvAppSubtitle.setText("Sword Of Women");
            }
        }

        private String readFullname() {
            android.content.SharedPreferences prefs =
                    requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE);
            return prefs.getString("fullname", null);
        }
    }
