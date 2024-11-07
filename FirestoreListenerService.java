package com.example.laporan2;

import static android.content.ContentValues.TAG;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

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
                            String reportId = dc.getDocument().getString("reportId");
                            String senderName = dc.getDocument().getString("senderName");
                            if (!isNotificationShown(reportId)) {
                                showNotification(reportId, senderName);
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

    private void showNotification(String reportId, String senderName) {
        NotificationHelper notificationHelper = new NotificationHelper(this);
        notificationHelper.showNotification("Menerima Laporan",
                "Anda telah menerima laporan baru dari " + senderName);
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