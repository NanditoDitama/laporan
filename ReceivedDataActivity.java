package com.example.laporan2;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import androidx.appcompat.app.AlertDialog;
public class ReceivedDataActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private List<Report> reports;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ProgressBar progressBar;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_received_data);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        reports = new ArrayList<>();

        adapter = new HistoryAdapter(
                reports,
                report -> {
                    Intent intent = new Intent(this, ReportDetailActivity.class);
                    intent.putExtra("reportId", report.getId());
                    startActivity(intent);
                    updateReadStatus(report.getId());

                    // Perbarui tampilan
                    adapter.notifyDataSetChanged();
                },
                this::showDeleteConfirmationDialog,
                false  // false untuk ReceivedData
        );

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadReceivedHistory();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadReceivedHistory() {
        progressBar.setVisibility(View.VISIBLE);
        String currentUserId = mAuth.getCurrentUser().getUid();

        db.collection("sharedReports")
                .whereEqualTo("recipientId", currentUserId)
                .whereEqualTo("deletedByRecipient", false)
                .get()
                .addOnSuccessListener(sharedReportSnapshots -> {
                    reports.clear();
                    if (sharedReportSnapshots.isEmpty()) {
                        Toast.makeText(this, "No reports found", Toast.LENGTH_SHORT).show();
                    } else {
                        for (QueryDocumentSnapshot document : sharedReportSnapshots) {
                            String reportId = document.getString("reportId");
                            String senderName = document.getString("senderName");
                            boolean isReadRecipient = Boolean.TRUE.equals(document.getBoolean("isReadRecipient"));

                            db.collection("reports").document(reportId).get()
                                    .addOnSuccessListener(reportDoc -> {
                                        if (reportDoc.exists()) {
                                            Report report = reportDoc.toObject(Report.class);
                                            if (report != null) {
                                                report.setId(reportId);
                                                report.setSenderName(senderName);
                                                report.setReceiverName(currentUserId);
                                                report.setReadRecipient(isReadRecipient);
                                                Date timestamp = document.getDate("timestamp");
                                                report.setTimestamp(timestamp);
                                                reports.add(report);
                                                adapter.notifyDataSetChanged();
                                            }
                                        } else {
                                            Log.w("ReceivedDataActivity", "Report document does not exist: " + reportId);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("ReceivedDataActivity", "Error getting report document: ", e);
                                    });
                        }
                    }
                    progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("ReceivedDataActivity", "Error fetching shared reports: ", e);
                });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void deleteHistoryOnly(Report report) {
        progressBar.setVisibility(View.VISIBLE);
        String currentUserId = mAuth.getCurrentUser().getUid();

        db.collection("sharedReports")
                .whereEqualTo("recipientId", currentUserId)
                .whereEqualTo("reportId", report.getId())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        QueryDocumentSnapshot document = (QueryDocumentSnapshot) querySnapshot.getDocuments().get(0);
                        document.getReference().update("deletedByRecipient", true)
                                .addOnSuccessListener(aVoid -> {
                                    checkAndDeleteReport(document); // Cek dan hapus jika perlu
                                    reports.remove(report);
                                    adapter.notifyDataSetChanged();
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this, "Report removed from received list", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this, "Error updating report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "No shared report found to remove", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error finding shared report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void checkAndDeleteReport(QueryDocumentSnapshot document) {
        boolean deletedBySender = document.getBoolean("deletedBySender") != null && document.getBoolean("deletedBySender");

        if (deletedBySender) {
            // Hapus dokumen dari Firestore
            document.getReference().delete()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Report deleted from Firestore", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error deleting report from Firestore: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }
    private void showDeleteConfirmationDialog(Report report) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to delete this report?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    deleteHistoryOnly(report);
                })
                .setNegativeButton("No", null)
                .show();
    }
    private void updateReadStatus(String reportId) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser ().getUid();
        db.collection("sharedReports")
                .whereEqualTo("reportId", reportId)
                .whereEqualTo("recipientId", currentUserId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        document.getReference().update("isReadRecipient", true)
                                .addOnSuccessListener(aVoid -> {
                                    // Update the local report object
                                    for (Report report : reports) {
                                        if (report.getId().equals(reportId)) {
                                            report.setReadRecipient(true);
                                            adapter.notifyDataSetChanged(); // Notify adapter
                                            break;
                                        }
                                    }
                                });
                    }
                });
    }

}