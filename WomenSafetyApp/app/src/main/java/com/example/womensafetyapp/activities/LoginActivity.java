package com.example.womensafetyapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.womensafetyapp.R;
import com.example.womensafetyapp.firebase.FirebaseSyncManager;
import com.example.womensafetyapp.database.ContactDatabaseHelper;

import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;

public class LoginActivity extends AppCompatActivity {

    private EditText edtEmail, edtPassword;
    private Button btnLogin, btnGoogleSignIn;
    private TextView tvRegister;
    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;
    private static final int RC_SIGN_IN = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        tvRegister = findViewById(R.id.tvRegister);
        auth = FirebaseAuth.getInstance();

        // 🔹 Config Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        btnLogin.setOnClickListener(v -> loginWithEmail());
        btnGoogleSignIn.setOnClickListener(v -> loginWithGoogle());
        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class))
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Nếu user đã đăng nhập sẵn => tự đồng bộ danh bạ
        if (auth.getCurrentUser() != null) {
            syncContactsThenGoHome();
        }
    }

    private void loginWithEmail() {
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        syncContactsThenGoHome(); // ⬅️ Thêm vào đây
                    } else {
                        Exception e = task.getException();
                        String errorMessage = "Login failed";
                        if (e instanceof FirebaseAuthInvalidUserException) {
                            errorMessage = "This email is not registered.";
                        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                            errorMessage = "Invalid email or password.";
                        } else if (e.getMessage() != null && e.getMessage().contains("formatted")) {
                            errorMessage = "Please enter a valid email address.";
                        }

                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                    }

                });
    }

    private void loginWithGoogle() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        syncContactsThenGoHome(); // ⬅️ Thêm vào đây
                    } else {
                        Toast.makeText(this, "Firebase Auth failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 🟢 Hàm đồng bộ danh bạ Firebase ↔ SQLite
    private void syncContactsThenGoHome() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            goHome();
            return;
        }

        String uid = user.getUid();
        FirebaseSyncManager sync = new FirebaseSyncManager();
        ContactDatabaseHelper localDb = new ContactDatabaseHelper(this);

//        // 🧹 Xoá toàn bộ danh bạ cũ trước khi tải danh bạ mới
//        localDb.wipeContacts();
//        localDb.wipeProfile();

        // 🔄 Tải danh bạ của tài khoản hiện tại từ Firebase về
        sync.fetchEmergencyContacts(uid, localDb);
        sync.fetchProfileToLocal(uid, localDb);
        goHome(); // Tiếp tục vào MainActivity
    }


    private void goHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
