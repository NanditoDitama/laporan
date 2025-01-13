package com.example.laporan2;

import static android.app.Service.START_STICKY;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class FirestoreNotificationService extends Service {
    private static final String CHANNEL_ID = "FirestoreNotificationServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private ListenerRegistration listenerRegistration;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Firestore Notification Listener",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background service for listening to Firestore notifications");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Buat foreground notification
        Notification notification = createForegroundNotification();

        // Mulai service sebagai foreground
        startForeground(NOTIFICATION_ID, notification);

        startListeningForNotifications();
        return START_STICKY;
    }

    private Notification createForegroundNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Notification Listener")
                .setContentText("Mendengarkan notifikasi")
                .setSmallIcon(R.drawable.ic_notification) // Pastikan icon tersedia
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true); // Membuat notification tidak dapat dihapus

        return builder.build();
    }

    private void startListeningForNotifications() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            stopSelf();
            return;
        }

        String currentUserId = auth.getCurrentUser().getUid();

        listenerRegistration = FirebaseFirestore.getInstance()
                .collection("notifications")
                .whereEqualTo("userId", currentUserId)
                .whereEqualTo("isRead", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w("NotificationService", "Listen failed.", e);
                        return;
                    }

                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                DocumentSnapshot document = dc.getDocument();
                                showNotification(document);
                                markNotificationAsRead(document.getId());
                            }
                        }
                    }
                });
    }

    private void showNotification(DocumentSnapshot document) {
        NotificationHelper notificationHelper = new NotificationHelper(this);

        String title = document.getString("title");
        String message = document.getString("message");
        String reportId = document.getString("reportId");
        String notificationId = document.getId(); // Ambil ID dokumen notifikasi

        // Buat intent untuk membuka detail laporan
        Intent intent = new Intent(this, ReportDetailActivity.class);
        intent.putExtra("reportId", reportId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Tampilkan notifikasi
        notificationHelper.showNotificationWithIntent(title, message, intent);

        // Hapus dokumen notifikasi setelah ditampilkan
        FirebaseFirestore.getInstance()
                .collection("notifications")
                .document(notificationId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d("NotificationService", "Notification document deleted: " + notificationId);
                })
                .addOnFailureListener(e -> {
                    Log.e("NotificationService", "Failed to delete notification document", e);
                });
    }

    private void markNotificationAsRead(String notificationId) {
        FirebaseFirestore.getInstance()
                .collection("notifications")
                .document(notificationId)
                .update("isRead", true)
                .addOnFailureListener(e -> Log.e("NotificationService", "Failed to mark notification as read", e));
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