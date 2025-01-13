package com.example.laporan2;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class FirestoreListenerService extends Service {
    private FirebaseFirestore db;
    private ListenerRegistration listenerRegistration;
    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate() {
        super.onCreate();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startListening();
        return START_STICKY;
    }

    private void startListening() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        listenerRegistration = db.collection("sharedReports")
                .whereEqualTo("recipientId", currentUserId)
                .whereEqualTo("isReadRecipient", false)
                .whereEqualTo("isReadSender", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w("FirestoreListener", "Listen failed.", e);
                        return;
                    }

                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            DocumentSnapshot document = dc.getDocument();
                            String reportId = document.getString("reportId");

                            if (!isNotificationShown(reportId)) {
                                showNotification(document);
                                markNotificationAsShown(reportId);
                            }
                        }
                    }
                });
    }

    private boolean isNotificationShown(String reportId) {
        return sharedPreferences.getBoolean(reportId, false);
    }

    private void markNotificationAsShown(String reportId) {
        sharedPreferences.edit().putBoolean(reportId, true).apply();
    }

    private void showNotification(DocumentSnapshot document) {
        String reportId = document.getString("reportId");
        String senderName = document.getString("senderName");
        String type = document.getString("type");
        String sharedReportId = document.getId(); // Ambil ID dokumen shared report

        Intent intent;
        if (type != null && type.equals("newReport")) {
            // Jika type adalah "newReport", arahkan ke AdminReportDetailActivity
            intent = new Intent(this, AdminReportDetailActivity.class);
            intent.putExtra("reportId", reportId);
        } else {
            // Jika tidak ada type atau type berbeda, arahkan ke ReceivedDataActivity
            intent = new Intent(this, ReceivedDataActivity.class);
            intent.putExtra("reportId", reportId);
        }

        NotificationHelper notificationHelper = new NotificationHelper(this);
        notificationHelper.showNotificationWithIntent("Menerima Laporan",
                "Anda telah menerima laporan baru dari " + senderName, intent);

        // Hapus dokumen shared report setelah notifikasi ditampilkan
        FirebaseFirestore.getInstance()
                .collection("sharedReports")
                .document(sharedReportId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirestoreListenerService", "Shared report document deleted: " + sharedReportId);
                })
                .addOnFailureListener(e -> {
                    Log.e("FirestoreListenerService", "Failed to delete shared report document", e);
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}