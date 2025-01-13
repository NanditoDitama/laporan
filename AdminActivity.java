package com.example.laporan2;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
public class AdminActivity extends AppCompatActivity {
    private static final int NOTIFICATION_PERMISSION_CODE = 123;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private AdminPagerAdapter pagerAdapter;
    private FirebaseAuth mAuth;
    private ImageButton buttonLogout;
    private FirebaseFirestore db;
    private FloatingActionButton fabSearch;
    private SwipeRefreshLayout swipeRefreshLayout;
    private static final String NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);
        checkNotificationPermission();
        // Inisialisasi Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Inisialisasi View
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        buttonLogout = findViewById(R.id.buttonLogout);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        if (!FirebaseApp.getApps(this).isEmpty()) {
            FirebaseFirestore.getInstance();
        } else {
            FirebaseApp.initializeApp(this);
            FirebaseFirestore.getInstance();
        }
        // Buat dan set adapter untuk ViewPager
        pagerAdapter = new AdminPagerAdapter(getSupportFragmentManager(), getLifecycle());
        viewPager.setAdapter(pagerAdapter);

        // Hubungkan TabLayout dengan ViewPager
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Data Diproses");
                    break;
                case 1:
                    tab.setText("Data Baru");
                    break;
                case 2:
                    tab.setText("Data Edit");
                    break;
            }
        }).attach();

        // Inisialisasi tombol logout
        buttonLogout.setOnClickListener(v -> showLogoutConfirmationDialog());
        setupSwipeRefresh();
    }


    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Refresh semua fragment
            refreshAllFragmentData();
        });
    }



    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, NOTIFICATION_PERMISSION) !=
                    PackageManager.PERMISSION_GRANTED) {

                showNotificationPermissionDialog();
            }
        }
    }

    private void showNotificationPermissionDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Izin Notifikasi")
                .setMessage("Aplikasi ini memerlukan izin notifikasi untuk memberi tahu Anda saat ada laporan baru yang dibagikan.")
                .setPositiveButton("Izinkan", (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ActivityCompat.requestPermissions(AdminActivity.this,
                                new String[]{NOTIFICATION_PERMISSION},
                                NOTIFICATION_PERMISSION_CODE);
                    }
                })
                .setNegativeButton("Nanti", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Izin notifikasi diberikan", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Izin notifikasi ditolak", Toast.LENGTH_SHORT).show();
            }
        }
    }
    // Modifikasi metode refreshAllFragmentData untuk menangani kesalahan jaringan
    private void refreshAllFragmentData() {
        // Pastikan pagerAdapter tidak null
        if (pagerAdapter != null) {
            pagerAdapter.refreshAllFragments();
        }

        // Cek koneksi Firestore
        db.collection("reports")
                .limit(1)
                .get()
                .addOnFailureListener(error -> {
                    if (error instanceof FirebaseFirestoreException) {
                        FirebaseFirestoreException firestoreError = (FirebaseFirestoreException) error;

                        if (firestoreError.getCode() == FirebaseFirestoreException.Code.UNAVAILABLE ||
                                firestoreError.getCode() == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                            // Kesalahan jaringan
                            showNetworkErrorDialog(firestoreError.getMessage());
                        }
                    }
                })
                .addOnCompleteListener(task -> {
                    // Hentikan animasi refresh setelah beberapa saat
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (swipeRefreshLayout != null) {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                    }, 2000); // Delay 2 detik
                });
    }

    private void showNetworkErrorDialog(String errorMessage) {
        new AlertDialog.Builder(this)
                .setTitle("Kesalahan Jaringan")
                .setMessage("Terjadi masalah koneksi: " + errorMessage + "\n\nApakah Anda ingin mencoba lagi?")
                .setPositiveButton("Coba Lagi", (dialog, which) -> {
                    // Refresh semua fragment
                    refreshAllFragmentData();
                })
                .setNegativeButton("Batal", (dialog, which) -> {
                    // Tutup aktivitas atau lakukan tindakan lain
                    finish();
                })
                .setCancelable(false)
                .show();
    }



    // Metode untuk memproses laporan (digunakan di fragment)
    public void processReport(Report report, String status, String rejectReason) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Memproses laporan...");
        progressDialog.show();

        // Persiapkan data laporan yang diproses
        Map<String, Object> processedReportData = new HashMap<>();
        processedReportData.put("title", report.getTitle());
        processedReportData.put("description", report.getDescription());
        processedReportData.put("amount", report.getAmount());
        processedReportData.put("date", report.getDate());
        processedReportData.put("imageUrl", report.getImageUrl());
        processedReportData.put("userId", report.getUserId());

        // PENTING: Pastikan status diset dengan benar
        processedReportData.put("status", "rejected"); // Eksplisit set ke "rejected"
        processedReportData.put("timestamp", FieldValue.serverTimestamp());

        // Tambahkan alasan penolakan
        if (rejectReason != null) {
            processedReportData.put("rejectReason", rejectReason);
        }

        // Update status di collection reports
        Map<String, Object> reportUpdate = new HashMap<>();
        reportUpdate.put("status", "rejected"); // Pastikan status di reports juga "rejected"
        if (rejectReason != null) {
            reportUpdate.put("rejectReason", rejectReason);
        }

        db.collection("reports")
                .document(report.getId())
                .update(reportUpdate)
                .addOnSuccessListener(aVoid -> {
                    // Simpan ke koleksi processedReports
                    db.collection("processedReports")
                            .add(processedReportData)
                            .addOnSuccessListener(docRef -> {
                                // Kirim notifikasi
                                sendProcessNotification(report, "rejected", rejectReason);

                                progressDialog.dismiss();
                                Toast.makeText(this, "Laporan berhasil ditolak", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                progressDialog.dismiss();
                                Toast.makeText(this, "Gagal memproses laporan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Gagal memperbarui status laporan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // Metode untuk mengirim notifikasi
    private void sendProcessNotification(Report report, String status, String rejectReason) {
        String title = status.equals("approved") ? "Laporan Disetujui" : "Laporan Ditolak";
        String message = status.equals("approved")
                ? "Laporan Anda telah disetujui"
                : "Laporan Anda ditolak. Alasan: " + (rejectReason != null ? rejectReason : "Tidak ada alasan");

        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", report.getUserId());
        notification.put("title", title);
        notification.put("message", message);
        notification.put("timestamp", FieldValue.serverTimestamp());
        notification.put("isRead", false);

        db.collection("notifications")
                .add(notification)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Gagal mengirim notifikasi", Toast.LENGTH_SHORT).show()
                );
    }

    // Metode untuk menampilkan dialog penolakan
    public void showRejectReasonDialog(Report report) {
        if (report == null) {
            Toast.makeText(this, "Laporan tidak valid", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reject_reason, null);
        EditText editTextReason = dialogView.findViewById(R.id.editTextRejectReason);

        builder.setTitle("Alasan Penolakan")
                .setView(dialogView)
                .setPositiveButton("Tolak", (dialog, which) -> {
                    String rejectReason = editTextReason.getText().toString().trim();
                    if (!rejectReason.isEmpty()) {
                        processReport(report, "rejected", rejectReason);
                    } else {
                        Toast.makeText(this, "Alasan penolakan harus diisi", Toast.LENGTH_SHORT).show();
                        // Tampilkan ulang dialog jika alasan kosong
                        showRejectReasonDialog(report);
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // Metode untuk konfirmasi logout
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Konfirmasi Logout")
                .setMessage("Apakah Anda yakin ingin keluar?")
                .setPositiveButton("Ya", (dialog, which) -> performLogout())
                .setNegativeButton("Tidak", null)
                .show();
    }

    // Metode untuk melakukan logout
    private void performLogout() {
        // Tampilkan progress dialog saat logout
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Logging out...");
        progressDialog.show();

        // Proses logout
        mAuth.signOut();

        // Tunggu sebentar untuk proses sign out
        new Handler().postDelayed(() -> {
            progressDialog.dismiss();

            // Arahkan ke halaman login
            Intent intent = new Intent(AdminActivity.this, LoginActivity.class);
            // Bersihkan task sebelumnya
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);

            // Tutup aktivitas saat ini
            finish();
        }, 1000); // Delay 1 detik
    }

    // Metode untuk menangani tombol kembali
    @Override
    public void onBackPressed() {
        // Tampilkan dialog konfirmasi keluar
        super.onBackPressed();
        showExitConfirmationDialog();
    }

    // Metode untuk menampilkan dialog konfirmasi keluar
    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Konfirmasi Keluar")
                .setMessage("Apakah Anda yakin ingin keluar dari aplikasi?")
                .setPositiveButton("Ya", (dialog, which) -> {
                    // Tutup semua aktivitas dan keluar dari aplikasi
                    finishAffinity();
                    System.exit(0);
                })
                .setNegativeButton("Tidak", null)
                .show();
    }

    // Metode untuk menangani proses laporan yang sudah ada
    public void handleExistingReport(Report report, String action) {
        switch (action) {
            case "approve":
                processReport(report, "approved", null);
                break;
            case "reject":
                showRejectReasonDialog(report);
                break;
            default:
                Toast.makeText(this, "Aksi tidak valid", Toast.LENGTH_SHORT).show();
        }
    }

    // Metode untuk mendapatkan detail laporan
    public void fetchReportDetails(String reportId, OnReportFetchedListener listener) {
        db.collection("reports")
                .document(reportId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Report report = documentSnapshot.toObject(Report.class);
                        if (report != null) {
                            report.setId(documentSnapshot.getId());
                            listener.onReportFetched(report);
                        } else {
                            listener.onReportFetchError("Gagal mengonversi laporan");
                        }
                    } else {
                        listener.onReportFetchError("Laporan tidak ditemukan");
                    }
                })
                .addOnFailureListener(e ->
                        listener.onReportFetchError("Gagal mengambil laporan: " + e.getMessage())
                );
    }

    // Interface untuk callback pengambilan laporan
    public interface OnReportFetchedListener {
        void onReportFetched(Report report);
        void onReportFetchError(String errorMessage);
    }

    // Metode untuk mengirim ulang notifikasi
    public void resendNotification(Report report) {
        // Cek status laporan
        if (report.getStatus() != null) {
            switch (report.getStatus()) {
                case "approved":
                    sendProcessNotification(report, "approved", null);
                    break;
                case "rejected":
                    sendProcessNotification(report, "rejected", report.getRejectReason());
                    break;
                default:
                    Toast.makeText(this, "Status laporan tidak valid", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Metode untuk mendapatkan laporan berdasarkan status
    public void fetchReportsByStatus(String status, OnReportsListFetchedListener listener) {
        db.collection("reports")
                .whereEqualTo("status", status)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Report> reportsList = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Report report = document.toObject(Report.class);
                        report.setId(document.getId());
                        reportsList.add(report);
                    }
                    listener.onReportsFetched(reportsList);
                })
                .addOnFailureListener(e ->
                        listener.onReportsFetchError("Gagal mengambil laporan: " + e.getMessage())
                );
    }

    // Interface untuk callback pengambilan daftar laporan
    public interface OnReportsListFetchedListener {
        void onReportsFetched(List<Report> reports);
        void onReportsFetchError(String errorMessage);
    }
}