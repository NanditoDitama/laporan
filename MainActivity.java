package com.example.laporan2;

import static android.content.ContentValues.TAG;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private TextView textViewWelcome;
    private ImageButton buttonMenu;
    private ListView listViewReports;
    private FloatingActionButton fabAddReport;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ReportAdapter adapter;
    private List<Report> reportsList;
    private FirebaseStorage storage;
    private ListenerRegistration reportListener;
    private static final int NOTIFICATION_PERMISSION_CODE = 123;
    private static final int ADD_REPORT_REQUEST = 1;
    private static final int EDIT_REPORT_REQUEST = 2;
    private static final String NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        createNotificationChannel();
        Intent serviceIntent = new Intent(this, FirestoreListenerService.class);
        startService(serviceIntent);

        Log.d(TAG, "MainActivity onCreate");
        buttonMenu = findViewById(R.id.buttonMenu);
        buttonMenu.setOnClickListener(v -> showPopupMenu());

        // Inisialisasi Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        checkNotificationPermission();
        startFirestoreListenerService();

        // Cek login status
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Inisialisasi views dan setup komponen lainnya
        initializeViews();
        setupListeners();

        // Fetch dan set welcome message
        fetchUserData(currentUser);

        // Load data
        fetchReports();
    }
    private void startFirestoreListenerService() {
        Intent serviceIntent = new Intent(this, FirestoreListenerService.class);
        startService(serviceIntent);
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "LaporanChannel";
            String description = "Channel for Laporan notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH; // Ubah ke HIGH
            NotificationChannel channel = new NotificationChannel("LAPORAN_CHANNEL", name, importance);
            channel.setDescription(description);

            // Tambahkan pengaturan tambahan untuk channel
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }


    private void fetchUserData(FirebaseUser currentUser) {
        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String userName = documentSnapshot.getString("name");
                        if (userName != null && !userName.isEmpty()) {
                            textViewWelcome.setText("Welcome, " + userName);
                        } else {
                            textViewWelcome.setText("Welcome, " + currentUser.getEmail());
                        }
                    } else {
                        textViewWelcome.setText("Welcome, " + currentUser.getEmail());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user data", e);
                    textViewWelcome.setText("Welcome, " + currentUser.getEmail());
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
        new AlertDialog.Builder(this)
                .setTitle("Izin Notifikasi")
                .setMessage("Aplikasi ini memerlukan izin notifikasi untuk memberi tahu Anda saat ada laporan baru yang dibagikan.")
                .setPositiveButton("Izinkan", (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ActivityCompat.requestPermissions(MainActivity.this,
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


    private void initializeViews() {
        textViewWelcome = findViewById(R.id.textViewWelcome);
        fabAddReport = findViewById(R.id.fabAddReport);
        listViewReports = findViewById(R.id.listViewReports);
        Button buttonSearch = findViewById(R.id.buttonSearch);
        buttonSearch.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SearchActivity.class));
        });
        // Inisialisasi list dan adapter
        reportsList = new ArrayList<>();
        adapter = new ReportAdapter(this, reportsList);
        listViewReports.setAdapter(adapter);
    }

    private void setupListeners() {

        fabAddReport.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddReportActivity.class);
            startActivityForResult(intent, ADD_REPORT_REQUEST);
        });

        listViewReports.setOnItemClickListener((parent, view, position, id) -> {
            Report report = reportsList.get(position);
            Intent intent = new Intent(MainActivity.this, ReportDetailActivity.class);
            intent.putExtra("report", report);
            startActivity(intent);
        });

        adapter.setOnItemClickListener(new ReportAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Report report) {
                Intent intent = new Intent(MainActivity.this, ReportDetailActivity.class);
                intent.putExtra("reportId", report.getId());
                startActivity(intent);
            }

            @Override
            public void onDeleteClick(Report report) {
                showDeleteConfirmationDialog(report);
            }

            @Override
            public void onEditClick(Report report) {
                Intent intent = new Intent(MainActivity.this, EditReportActivity.class);
                intent.putExtra("reportId", report.getId());
                startActivityForResult(intent, EDIT_REPORT_REQUEST);
            }
        });
    }


    private void showPopupMenu() {
        PopupMenu popup = new PopupMenu(this, buttonMenu);
        popup.getMenuInflater().inflate(R.menu.main_menu, popup.getMenu());

        // Dapatkan data user
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            // Dapatkan item menu email
            MenuItem emailItem = popup.getMenu().findItem(R.id.email);

            // Set title dengan nama dan email
            String userInfo =  user.getEmail();
            emailItem.setTitle(userInfo);
        }

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_send_data) {
                startActivity(new Intent(MainActivity.this, SentDataActivity.class));
                return true;
            } else if (itemId == R.id.menu_received_data) {
                startActivity(new Intent(MainActivity.this, ReceivedDataActivity.class));
                return true;
            } else if (itemId == R.id.menu_logout) {
                logout();
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void logout() {
        mAuth.signOut();
        startActivity(new Intent(MainActivity.this, LoginActivity.class));
        finish();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            switch (requestCode) {
                case ADD_REPORT_REQUEST:
                    Report newReport = data.getParcelableExtra("newReport");
                    if (newReport != null) {
                        reportsList.add(0, newReport);
                        adapter.notifyDataSetChanged();
                    }
                    break;
                case EDIT_REPORT_REQUEST:
                    Report updatedReport = data.getParcelableExtra("updatedReport");
                    if (updatedReport != null) {
                        int index = findReportIndex(updatedReport.getId());
                        if (index != -1) {
                            reportsList.set(index, updatedReport);
                            adapter.notifyDataSetChanged();
                        }
                    }
                    break;
            }
        }
    }

    private int findReportIndex(String reportId) {
        for (int i = 0; i < reportsList.size(); i++) {
            if (reportsList.get(i).getId().equals(reportId)) {
                return i;
            }
        }
        return -1;
    }

    private void fetchReports() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No user is currently logged in");
            Toast.makeText(MainActivity.this, "Silakan login terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = currentUser.getUid();
        reportListener = db.collection("reports")
                .whereEqualTo("userId", userId) // Pastikan ini ada
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error fetching reports: ", error);
                        Toast.makeText(MainActivity.this, "Gagal mengambil laporan: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    reportsList.clear();
                    if (value != null && !value.isEmpty()) {
                        for (QueryDocumentSnapshot document : value) {
                            Report report = document.toObject(Report.class);
                            report.setId(document.getId());
                            reportsList.add(report);
                        }
                        // Periksa apakah jumlah data mencapai limit
                        if (value.size() >= 100) {
                            showLimitReachedDialog();
                            // Nonaktifkan FAB untuk menambah laporan baru
                            fabAddReport.setEnabled(false);
                            fabAddReport.setAlpha(0.5f);
                        } else {
                            // Aktifkan kembali FAB jika jumlah data di bawah limit
                            fabAddReport.setEnabled(true);
                            fabAddReport.setAlpha(1.0f);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void showLimitReachedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Batas Data Tercapai")
                .setMessage("Anda telah mencapai batas 100 laporan. Untuk menambah laporan baru, " +
                        "Anda perlu menghapus beberapa laporan lama yang tidak digunakan.")
                .setPositiveButton("Mengerti", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reportListener != null) {
            reportListener.remove();
        }
    }

    private void showDeleteConfirmationDialog(Report report) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogCustom);

        View customView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
        TextView titleText = customView.findViewById(R.id.dialog_title);
        TextView messageText = customView.findViewById(R.id.dialog_message);

        titleText.setText("Konfirmasi Hapus");
        messageText.setText("Apakah Anda yakin ingin menghapus laporan ini?");

        builder.setView(customView);

        AlertDialog dialog = builder.create();

        Button buttonPositive = customView.findViewById(R.id.button_positive);
        Button buttonNegative = customView.findViewById(R.id.button_negative);

        buttonPositive.setOnClickListener(v -> deleteReport(report, dialog));
        buttonNegative.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void deleteReport(Report report, AlertDialog dialog) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Menghapus laporan...");
        progressDialog.show();

        // Hapus dokumen dari Firestore
        db.collection("reports").document(report.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Jika berhasil, hapus gambar dari Storage
                    if (report.getImageUrl() != null && !report.getImageUrl().isEmpty()) {
                        StorageReference photoRef = storage.getReferenceFromUrl(report.getImageUrl());
                        photoRef.delete().addOnSuccessListener(aVoid1 -> {
                            progressDeleteSuccess(progressDialog, dialog, report);
                        }).addOnFailureListener(e -> {
                            progressDeleteFailure(progressDialog, dialog, report);
                        });
                    } else {
                        progressDeleteSuccess(progressDialog, dialog, report);
                    }
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Gagal menghapus laporan", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
    }

    private void progressDeleteSuccess(ProgressDialog progressDialog, AlertDialog dialog, Report report) {
        progressDialog.dismiss();
        Toast.makeText(this, "Laporan berhasil dihapus", Toast.LENGTH_SHORT).show();
        reportsList.remove(report);
        adapter.notifyDataSetChanged();
        dialog.dismiss();
    }

    private void progressDeleteFailure(ProgressDialog progressDialog, AlertDialog dialog, Report report) {
        progressDialog.dismiss();
        Toast.makeText(this, "Laporan terhapus, tetapi gagal menghapus gambar", Toast.LENGTH_SHORT).show();
        reportsList.remove(report);
        adapter.notifyDataSetChanged();
        dialog.dismiss();
    }

}