package com.example.laporan2;

import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminReportAdapter extends RecyclerView.Adapter<AdminReportAdapter.ViewHolder> {
    private Context context;
    private List<Report> reports;
    private OnApproveClickListener approveListener;
    private OnRejectClickListener rejectListener;
    private OnItemClickListener itemClickListener;
    // Definisikan antarmuka untuk listener
    public interface OnApproveClickListener {
        void onApprove(Report report);
    }

    public interface OnRejectClickListener {
        void onReject(Report report);
    }

    public interface OnItemClickListener {
        void onItemClick(Report report);
    }

    // Konstruktor
    public AdminReportAdapter(
            Context context,
            List<Report> reports,
            OnApproveClickListener approveListener,
            OnRejectClickListener rejectListener,
            OnItemClickListener itemClickListener
    ) {
        this.context = context;
        this.reports = reports;
        this.approveListener = approveListener;
        this.rejectListener = rejectListener;
        this.itemClickListener = itemClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Tambahkan null check
        if (context == null) {
            context = parent.getContext();
        }

        View view = LayoutInflater.from(context).inflate(R.layout.item_admin_data, parent, false);
        return new ViewHolder(view);
    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Report report = reports.get(position);

        // Set pengirim
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(report.getUserId())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String senderName = documentSnapshot.getString("name");
                        holder.textViewSender.setText("Pengirim: " + senderName);
                    }
                });

        // Set judul dan tanggal
        holder.textViewTitle.setText(report.getTitle());
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        holder.textViewDate.setText(dateFormat.format(report.getDate()));

        // Set item click listener
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(report);
            }
        });

        // Set approve listener
        holder.buttonApprove.setOnClickListener(v -> {
            if (approveListener != null) {
                approveListener.onApprove(report);
                updateReportStatus(report, "approved", null);
            }
        });

        // Set reject listener
        holder.buttonReject.setOnClickListener(v -> {
            if (rejectListener != null) {
                rejectListener.onReject(report);
                showRejectDialog(report); // Hapus parameter context
            }
        });
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewSender, textViewTitle, textViewDate;
        Button buttonApprove, buttonReject;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewSender = itemView.findViewById(R.id.textViewSender);
            textViewTitle = itemView.findViewById(R.id.textViewTitle);
            textViewDate = itemView.findViewById(R.id.textViewDate);
            buttonApprove = itemView.findViewById(R.id.buttonApprove);
            buttonReject = itemView.findViewById(R.id.buttonReject);
        }
    }

    private void showRejectDialog(Report report) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_reject_reason, null);
        EditText editTextReason = dialogView.findViewById(R.id.editTextRejectReason);

        builder.setTitle("Alasan Penolakan")
                .setView(dialogView)
                .setPositiveButton("Tolak", null) // Set null untuk menunda penanganan tombol
                .setNegativeButton("Batal", null);

        // Buat dialog
        AlertDialog dialog = builder.create();

        // Set listener untuk tombol positif
        dialog.setOnShowListener(dialogInterface -> {
            Button buttonPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            buttonPositive.setOnClickListener(v -> {
                String rejectReason = editTextReason.getText().toString().trim();

                if (rejectReason.isEmpty()) {
                    Toast.makeText(context, "Alasan penolakan harus diisi", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Update status di Firestore untuk koleksi reports
                updateReportStatus(report, "rejected", rejectReason);

                // Tutup dialog setelah berhasil
                dialog.dismiss();
            });
        });

        dialog.show(); // Tampilkan dialog
    }

    private void updateReportStatus(Report report, String status, String rejectReason) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Siapkan data update untuk koleksi reports
        Map<String, Object> reportUpdateData = new HashMap<>();
        reportUpdateData.put("status", status);
        if (rejectReason != null) {
            reportUpdateData.put("rejectReason", rejectReason);
        }
        reportUpdateData.put("timestamp", FieldValue.serverTimestamp());

        // Update status di koleksi reports
        db.collection("reports")
                .document(report.getId())
                .update(reportUpdateData)
                .addOnSuccessListener(aVoid -> {
                    sendNotificationToUser(report, status, rejectReason);
                    // Setelah berhasil memperbarui status di reports, simpan ke processedReports
                    checkAndUpdateProcessedReports(report, status, rejectReason);
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminReportAdapter", "Gagal memperbarui status laporan: " + e.getMessage());
                    Toast.makeText(context, "Gagal memperbarui status laporan", Toast.LENGTH_SHORT).show();
                });
    }
    private void sendNotificationToUser(Report report, String status, String rejectReason) {
        // Cari dokumen sharedReports terkait
        FirebaseFirestore.getInstance()
                .collection("reports")
                .whereEqualTo("reportId", report.getId())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String senderId = document.getString("senderId");

                        // Buat notifikasi untuk pengirim laporan
                        Map<String, Object> notificationData = new HashMap<>();
                        notificationData.put("userId", senderId);
                        notificationData.put("reportId", report.getId());
                        notificationData.put("status", status);
                        notificationData.put("title", status.equals("approved") ?
                                "Laporan Disetujui" : "Laporan Ditolak");
                        notificationData.put("message", status.equals("approved") ?
                                "Laporan Anda telah disetujui" :
                                "Laporan Anda ditolak. Alasan: " + (rejectReason != null ? rejectReason : "Tidak ada alasan"));
                        notificationData.put("timestamp", FieldValue.serverTimestamp());
                        notificationData.put("isRead", false);

                        // Simpan notifikasi
                        FirebaseFirestore.getInstance()
                                .collection("notifications")
                                .add(notificationData);

                        // Update status dokumen sharedReports
                        document.getReference()
                                .update("isReadRecipient", true);
                    }
                });
    }
    private void checkAndUpdateProcessedReports(Report report, String status, String rejectReason) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Cek apakah dokumen di processedReports sudah ada
        db.collection("processedReports")
                .whereEqualTo("reportId", report.getId())
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // Dokumen sudah ada, langsung update
                        DocumentSnapshot documentSnapshot = queryDocumentSnapshots.getDocuments().get(0);
                        String processedReportDocId = documentSnapshot.getId();

                        // Update dokumen yang sudah ada
                        updateExistingProcessedReport(processedReportDocId, status, rejectReason);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminReportAdapter", "Gagal mencari dokumen processedReports: " + e.getMessage());
                });
    }

    private void updateExistingProcessedReport(String processedReportDocId, String status, String rejectReason) {
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("status", status);

        // Tambahkan alasan penolakan hanya jika ada
        if (rejectReason != null) {
            updateData.put("rejectReason", rejectReason);
        }

        updateData.put("timestamp", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection("processedReports")
                .document(processedReportDocId)
                .update(updateData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(context, "Status laporan berhasil diperbarui", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Gagal memperbarui status laporan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void createNewProcessedReportDocument(Report report, String status, String rejectReason) {
        Map<String, Object> processedReportData = new HashMap<>();

        // Data utama
        processedReportData.put("reportId", report.getId());
        processedReportData.put("userId", report.getUserId());
        processedReportData.put("status", status);
        processedReportData.put("timestamp", FieldValue.serverTimestamp());

        // Tambahkan alasan penolakan jika ada
        if (rejectReason != null) {
            processedReportData.put("rejectReason", rejectReason);
        }

        // Data tambahan dari laporan
        processedReportData.put("title", report.getTitle());
        processedReportData.put("description", report.getDescription());
        processedReportData.put("amount", report.getAmount());
        processedReportData.put("date", report.getDate());
        processedReportData.put("imageUrl", report.getImageUrl());

        FirebaseFirestore.getInstance()
                .collection("processedReports")
                .add(processedReportData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(context, "Laporan berhasil diproses", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Gagal memproses laporan", Toast.LENGTH_SHORT).show();
                });
    }

}