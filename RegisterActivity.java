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
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;



import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import android.app.AlertDialog;

public class RegisterActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN = 9001;
    private GoogleSignInClient mGoogleSignInClient;
    private Button buttonGoogleSignIn;
    private EditText editTextEmail, editTextPassword;
    private Button buttonRegister;
    private TextView textViewLogin;
    private ImageView imageViewTogglePassword;
    private FirebaseAuth mAuth;
    private boolean isPasswordVisible = false;
    private EditText editTextName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);


        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);



        // Inisialisasi komponen UI
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonRegister = findViewById(R.id.buttonRegister);
        textViewLogin = findViewById(R.id.textViewLogin);
        imageViewTogglePassword = findViewById(R.id.imageViewTogglePassword);
        editTextName = findViewById(R.id.editTextName);
        mAuth = FirebaseAuth.getInstance();

        // Set onClickListener untuk tombol register
        buttonRegister.setOnClickListener(v -> registerUser());

        // Set onClickListener untuk textViewLogin
        textViewLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        // Set onClickListener untuk imageViewTogglePassword
        imageViewTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
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

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {

            editTextPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            imageViewTogglePassword.setImageResource(R.drawable.ic_visibility_off);
            isPasswordVisible = false;
        } else {

            editTextPassword.setTransformationMethod(null);
            imageViewTogglePassword.setImageResource(R.drawable.ic_visibility);
            isPasswordVisible = true;
        }
        // Pindahkan kursor ke akhir teks
        editTextPassword.setSelection(editTextPassword.getText().length());
    }

    private void registerUser() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        String name = editTextName.getText().toString().trim();

        // Validasi input
        if (name.isEmpty()) {
            Toast.makeText(RegisterActivity.this, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show();
            return;
        }
        if (email.isEmpty()) {
            Toast.makeText(RegisterActivity.this, "Email tidak boleh kosong", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.isEmpty()) {
            Toast.makeText(RegisterActivity.this, "Password tidak boleh kosong", Toast.LENGTH_SHORT).show();
            return;
        }

        // Tampilkan progress dialog
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Mendaftarkan akun...");
        progressDialog.show();

        // Proses registrasi
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Simpan data user ke Firestore
                            saveUserToFirestore(user, name);

                            // Update profile dengan nama
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();

                            user.updateProfile(profileUpdates)
                                    .addOnCompleteListener(profileTask -> {
                                        progressDialog.dismiss();
                                        if (profileTask.isSuccessful()) {
                                            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                                            finish();
                                        }
                                    });
                        }
                    } else {
                        progressDialog.dismiss();
                        Toast.makeText(RegisterActivity.this,
                                "Registrasi Gagal: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
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
        progressDialog.setMessage("Registering with Google...");
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
                                            Toast.makeText(RegisterActivity.this, "Login successful", Toast.LENGTH_SHORT).show();
                                            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                                            finish();
                                        } else {
                                            // User baru, tampilkan dialog input nama
                                            showNameInputDialog(user);
                                        }
                                    }
                                });
                    } else {
                        Toast.makeText(RegisterActivity.this,
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
                    Toast.makeText(RegisterActivity.this, "Registration successful", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(RegisterActivity.this, "Failed to save data: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }
}