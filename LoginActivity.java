package com.example.laporan2;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser ;


import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;


import android.app.AlertDialog;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN = 9001;
    private GoogleSignInClient mGoogleSignInClient;
    private Button buttonGoogleSignIn;

    private EditText editTextEmail, editTextPassword;
    private Button buttonLogin;
    private TextView textViewRegister;
    private ImageView imageViewTogglePassword;
    private boolean isPasswordVisible = false;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);


        mAuth = FirebaseAuth.getInstance();

        // Periksa apakah pengguna sudah login
        FirebaseUser  currentUser  = mAuth.getCurrentUser ();
        if (currentUser  != null) {
            // Jika pengguna sudah login, langsung arahkan ke MainActivity
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish(); // Tutup LoginActivity
            return; // Keluar dari metode onCreate
        }

        // Inisialisasi komponen UI
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        textViewRegister = findViewById(R.id.textViewRegister);
        imageViewTogglePassword = findViewById(R.id.imageViewTogglePassword);

        // Set onClickListener untuk tombol login
        buttonLogin.setOnClickListener(v -> loginUser ());

        // Set onClickListener untuk textViewRegister
        textViewRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });

        // Set onClickListener untuk toggle password visibility
        imageViewTogglePassword.setOnClickListener(v -> {
            if (isPasswordVisible) {
                // Sembunyikan password
                editTextPassword.setTransformationMethod(new PasswordTransformationMethod());
                imageViewTogglePassword.setImageResource(R.drawable.ic_visibility_off);
            } else {
                // Tampilkan password
                editTextPassword.setTransformationMethod(null);
                imageViewTogglePassword.setImageResource(R.drawable.ic_visibility);
            }
            isPasswordVisible = !isPasswordVisible;
            editTextPassword.setSelection(editTextPassword.length()); // Set cursor di akhir
        });
        buttonGoogleSignIn = findViewById(R.id.buttonGoogleSignIn);
        buttonGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
    }
    private void signInWithGoogle() {
        // Sign out dari akun yang ada
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            // Hapus akses sebelumnya
            mGoogleSignInClient.revokeAccess().addOnCompleteListener(this, task2 -> {
                // Buat intent sign in baru
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN);
            });
        });
    }



    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Toast.makeText(this, "Google sign in failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Logging in with Google...");
        progressDialog.show();

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();

                        // Cek apakah user sudah ada di Firestore
                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(user.getUid())
                                .get()
                                .addOnCompleteListener(task1 -> {
                                    if (task1.isSuccessful()) {
                                        if (task1.getResult().exists()) {
                                            // User sudah ada, langsung ke MainActivity
                                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                            finish();
                                        } else {
                                            // User baru, tampilkan dialog input nama
                                            showNameInputDialog(user);
                                        }
                                    }
                                });
                    } else {
                        Toast.makeText(LoginActivity.this,
                                "Authentication Failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showNameInputDialog(FirebaseUser user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_input_name, null);
        EditText editTextName = view.findViewById(R.id.editTextDialogName);

        builder.setView(view)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    String name = editTextName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        saveUserToFirestore(user, name);
                    } else {
                        Toast.makeText(this, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show();
                        showNameInputDialog(user); // Tampilkan dialog lagi jika nama kosong
                    }
                })
                .setCancelable(false); // User tidak bisa menutup dialog tanpa mengisi nama

        builder.create().show();
    }

    private void saveUserToFirestore(FirebaseUser user, String name) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String userId = user.getUid();
        String email = user.getEmail();

        // Buat array searchTerms untuk memudahkan pencarian
        List<String> searchTerms = new ArrayList<>();
        searchTerms.add(name.toLowerCase());
        searchTerms.add(email.toLowerCase());

        Map<String, Object> userData = new HashMap<>();
        userData.put("email", email);
        userData.put("name", name);
        userData.put("searchTerms", searchTerms);

        db.collection("users").document(userId)
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(LoginActivity.this, "Data berhasil disimpan", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(LoginActivity.this, "Gagal menyimpan data: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }



    private void loginUser() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(LoginActivity.this, "Email dan password harus diisi", Toast.LENGTH_SHORT).show();
            return;
        }

        // Tampilkan ProgressDialog
        ProgressDialog progressDialog = new ProgressDialog(LoginActivity.this);
        progressDialog.setMessage("Logging in...");
        progressDialog.show();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressDialog.dismiss(); // Tutup ProgressDialog
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        Toast.makeText(LoginActivity.this, "Login berhasil", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, "Login gagal: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}