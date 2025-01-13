package com.example.laporan2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable ;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NewDataFragment extends Fragment implements RefreshableFragment {
    private ListenerRegistration newReportsListener; // Listener untuk laporan baru
    private RecyclerView recyclerViewNewData;
    private ProgressBar progressBarNew;
    private TextView textViewEmptyNew;
    private FirebaseFirestore db;
    private List<Report> newReportsList;
    private AdminReportAdapter adapter;
    private static final int ADMIN_REPORT_DETAIL_REQUEST = 1001;



    @Override
    public void onStart() {
        super.onStart();
        fetchNewReports(); // Panggil fetchNewReports untuk mulai mendengarkan
    }

    @Override
    public void onStop() {
        super.onStop();
        if (newReportsListener != null) {
            newReportsListener.remove(); // Hapus listener saat fragment tidak aktif
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_new_data, container, false);

        recyclerViewNewData = view.findViewById(R.id.recyclerViewNewData);
        progressBarNew = view.findViewById(R.id.progressBarNew);
        textViewEmptyNew = view.findViewById(R.id.textViewEmptyNew);

        db = FirebaseFirestore.getInstance();
        newReportsList = new ArrayList<>();

        setupRecyclerView();
        fetchNewReports();

        return view;
    }
    @Override
    public void refreshData() {
        // Implementasi refresh untuk NewDataFragment
        fetchNewReports();
    }
    private void setupRecyclerView() {
        adapter = new AdminReportAdapter(
                requireContext(), // Gunakan requireContext() untuk fragment
                newReportsList,
                this::onReportApprove,
                this::onReportReject,
                this::onReportItemClick
        );
        recyclerViewNewData.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewNewData.setAdapter(adapter);
    }

    private void onReportItemClick(Report report) {
        // Buka halaman detail
        Intent intent = new Intent(getActivity(), AdminReportDetailActivity.class);
        intent.putExtra("reportId", report.getId());
        startActivity(intent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ADMIN_REPORT_DETAIL_REQUEST && resultCode == Activity.RESULT_OK) {
            // Muat ulang data
            fetchNewReports(); // Panggil metode untuk memuat ulang data
        }
    }

    private void onReportApprove(Report report) {
        processReport(report, "approved", null);
    }

    private void onReportReject(Report report) {
        showRejectReasonDialog(report);
    }

    private void showRejectReasonDialog(Report report) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reject_reason, null);
        EditText editTextReason = dialogView.findViewById(R.id.editTextRejectReason);

        builder.setTitle("Alasan Penolakan")
                .setView(dialogView)
                .setPositiveButton("Tolak", (dialog, which) -> {
                    String rejectReason = editTextReason.getText().toString().trim();

                    // Update status di Firestore untuk koleksi reports
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", "rejected");
                    updates.put("rejectReason", rejectReason);

                    db.collection("reports").document(report.getId())
                            .update(updates)
                            .addOnSuccessListener(aVoid -> {
                                // Simpan ke koleksi processedReports
                                saveToProcessedReports(report, "rejected", rejectReason);

                                // Hapus dari list
                                newReportsList.remove(report);
                                adapter.notifyDataSetChanged();

                                // Kirim notifikasi penolakan
                                sendRejectionNotification(report, rejectReason);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(getContext(), "Gagal menolak laporan", Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void sendRejectionNotification(Report report, String reason) {
        // Kirim notifikasi ke user bahwa laporannya ditolak
        FirebaseFirestore.getInstance()
                .collection("sharedReports")
                .whereEqualTo("reportId", report.getId())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String recipientId = doc.getString("recipientId");
                        sendNotificationToUser (recipientId,
                                "Laporan Ditolak",
                                "Laporan Anda dengan judul '" + report.getTitle() + "' ditolak. Alasan: " + reason
                        );
                    }
                });
    }

    private void sendNotificationToUser (String userId, String title, String message) {
        // Implementasi pengiriman notifikasi
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Anda bisa menambahkan logika notifikasi di sini
                        // Misalnya menggunakan Firebase Cloud Messaging
                        // Atau menambahkan ke collection notifications

                        // Contoh sederhana menambah ke collection notifications
                        Map<String, Object> notification = new HashMap<>();
                        notification.put("userId", userId);
                        notification.put("title", title);
                        notification.put("message", message);
                        notification.put("timestamp", FieldValue.serverTimestamp());
                        notification.put("isRead", false);

                        FirebaseFirestore.getInstance()
                                .collection("notifications")
                                .add(notification);
                    }
                });
    }

    private void fetchNewReports() {
        progressBarNew.setVisibility(View.VISIBLE);

        newReportsListener = db.collection("reports")
                .whereEqualTo("status", "pending")
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        progressBarNew.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Gagal mengambil data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    newReportsList.clear();
                    if (queryDocumentSnapshots != null) {
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            // Cek apakah field "EditedData" ada
                            if (!document.contains("EditedData")) {
                                Report report = document.toObject(Report.class);
                                report.setId(document.getId());
                                newReportsList.add(report);
                            }
                        }
                    }

                    progressBarNew .setVisibility(View.GONE);

                    if (newReportsList.isEmpty()) {
                        textViewEmptyNew.setVisibility(View.VISIBLE);
                        recyclerViewNewData.setVisibility(View.GONE);
                    } else {
                        textViewEmptyNew.setVisibility(View.GONE);
                        recyclerViewNewData.setVisibility(View.VISIBLE);
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private void saveToProcessedReports(Report report, String status, String rejectReason) {
        // Persiapkan data laporan yang diproses
        Map<String, Object> processedReportData = new HashMap<>();
        processedReportData.put("title", report.getTitle());
        processedReportData.put("description", report.getDescription());
        processedReportData.put("amount", report.getAmount());
        processedReportData.put("date", report.getDate());
        processedReportData.put("imageUrl", report.getImageUrl());
        processedReportData.put("userId", report.getUserId());
        processedReportData.put("status", status); // Set status ke "rejected"
        processedReportData.put("timestamp", FieldValue.serverTimestamp());

        // Tambahkan alasan penolakan jika status adalah rejected
        if ("rejected".equals(status)) {
            processedReportData.put("rejectReason", rejectReason);
        }

        // Simpan ke koleksi processedReports
        db.collection("processedReports")
                .add(processedReportData)
                .addOnSuccessListener(docRef -> {
                    // Log untuk konfirmasi
                    Log.d("ProcessedReport", "Report processed and saved successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e("ProcessedReport", "Failed to save processed report", e);
                });
    }

    private void processReport(Report report, String status, String rejectReason) {
        // Cek terlebih dahulu apakah dokumen sudah ada di processedReports
        db.collection("processedReports")
                .whereEqualTo("reportId", report.getId())
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // Dokumen sudah ada, update dokumen yang ada
                        DocumentSnapshot existingDoc = queryDocumentSnapshots.getDocuments().get(0);
                        String existingDocId = existingDoc.getId();

                        Map<String, Object> updateData = new HashMap<>();
                        updateData.put("status", status);
                        if (rejectReason != null) {
                            updateData.put("rejectReason", rejectReason);
                        }
                        updateData.put("timestamp", FieldValue.serverTimestamp());

                        db.collection("processedReports")
                                .document(existingDocId)
                                .update(updateData)
                                .addOnSuccessListener(aVoid -> {
                                    // Lanjutkan proses selanjutnya
                                    sendProcessNotification(report, status, rejectReason);
                                    newReportsList.remove(report);
                                    adapter.notifyDataSetChanged();
                                });
                    } else {
                        // Jika dokumen belum ada, baru tambahkan
                        Map<String, Object> processedReportData = new HashMap<>();
                        processedReportData.put("reportId", report.getId());
                        processedReportData.put("title", report.getTitle());
                        processedReportData.put("description", report.getDescription());
                        processedReportData.put("amount", report.getAmount());
                        processedReportData.put("date", report.getDate());
                        processedReportData.put("imageUrl", report.getImageUrl());
                        processedReportData.put("userId", report.getUserId());
                        processedReportData.put("status", status);
                        processedReportData.put("timestamp", FieldValue.serverTimestamp());

                        if (rejectReason != null) {
                            processedReportData.put("rejectReason", rejectReason);
                        }

                        db.collection("processedReports")
                                .add(processedReportData)
                                .addOnSuccessListener(docRef -> {
                                    // Lanjutkan proses selanjutnya
                                    sendProcessNotification(report, status, rejectReason);
                                    newReportsList.remove(report);
                                    adapter.notifyDataSetChanged();
                                });
                    }
                });
    }

    private void sendProcessNotification(Report report, String status, String rejectReason) {
        // Logika mengirim notifikasi
        String title = status.equals("approved") ? "Laporan Disetujui" : "Laporan Ditolak";
        String message = status.equals("approved")
                ? "Laporan Anda telah disetujui"
                : "Laporan Anda ditolak. Alasan: " + rejectReason;

        // Tambahkan ke collection notifications
        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", report.getUserId());
        notification.put("title", title);
        notification.put("message", message);
        notification.put("timestamp", FieldValue.serverTimestamp());
        notification.put("isRead", false);

        db.collection("notifications")
                .add(notification);
    }
}