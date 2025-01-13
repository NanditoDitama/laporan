package com.example.laporan2;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AdminReportDetailActivity extends AppCompatActivity {
    private TextView textViewTitle, textViewDescription, textViewSender,
            textViewDate, textViewAmount , textViewStatus;
    private ImageView imageViewReport;
    private Button buttonApprove, buttonReject;
    private FirebaseFirestore db;
    private String reportId;
    private ImageView imageViewEnlarged;

    private FrameLayout enlargedImageContainer;
    private Report report;
    private Matrix matrix = new Matrix();
    private float scale = 1f;
    private ScaleGestureDetector scaleGestureDetector;
    private float lastTouchX, lastTouchY;
    private float posX, posY;
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private int mode = NONE;
    private Matrix initialMatrix = new Matrix();
    private float initialScale = 1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Pastikan Firebase diinisialisasi
        try {
            // Inisialisasi Firebase secara eksplisit
            FirebaseApp.initializeApp(this);

            // Aktifkan logging untuk debugging
            FirebaseFirestore.setLoggingEnabled(true);

            // Inisialisasi Firestore
            db = FirebaseFirestore.getInstance();

            // Cek apakah db berhasil diinisialisasi
            if (db == null) {
                throw new IllegalStateException("FirebaseFirestore instance is null");
            }
        } catch (Exception e) {
            Log.e("AdminReportDetailActivity", "Firebase initialization error", e);
            Toast.makeText(this, "Gagal menginisialisasi database: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Set layout
        setContentView(R.layout.activity_admin_report_detail);

        // Inisialisasi View
        initializeViews();

        // Ambil reportId dari Intent
        reportId = getIntent().getStringExtra("reportId");

        // Validasi reportId
        if (reportId == null || reportId.isEmpty()) {
            Log.e("AdminReportDetailActivity", "ReportId is null or empty");
            Toast.makeText(this, "ID Laporan tidak valid", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Muat detail laporan
        loadReportDetails(reportId);
    }

    private void initializeViews() {
        textViewTitle = findViewById(R.id.textViewTitle);
        textViewDescription = findViewById(R.id.textViewDescription);
        textViewSender = findViewById(R.id.textViewSender);
        textViewDate = findViewById(R.id.textViewDate);
        textViewAmount = findViewById(R.id.textViewAmount);
        textViewStatus = findViewById(R.id.textViewStatus);
        imageViewReport = findViewById(R.id.imageViewReport);
        buttonApprove = findViewById(R.id.buttonApprove);
        buttonReject = findViewById(R.id.buttonReject);
        imageViewEnlarged = findViewById(R.id.imageViewEnlarged);
        enlargedImageContainer = findViewById(R.id.enlargedImageContainer);
        // Set up listener untuk tombol approve dan reject
        buttonApprove.setOnClickListener(v -> approveReport());
        buttonReject.setOnClickListener(v -> rejectReport());
    }

    private void loadReportDetails(String reportId) {
        db.collection("reports").document(reportId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        report = documentSnapshot.toObject(Report.class);

                        if (report != null) {
                            // Pastikan TextView tidak null sebelum mengatur teks
                            if (textViewTitle != null) {
                                textViewTitle.setText(report.getTitle());
                            }

                            if (textViewDescription != null) {
                                textViewDescription.setText(report.getDescription());
                            }

                            // Format amount
                            if (textViewAmount != null) {
                                DecimalFormat formatter = new DecimalFormat("#,###");
                                textViewAmount.setText("Rp " + formatter.format(report.getAmount()));
                            }

                            // Format tanggal
                            if (textViewDate != null) {
                                SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault());
                                textViewDate.setText(dateFormat.format(report.getDate()));
                            }

                            // Tampilkan gambar jika ada
                            if (imageViewReport != null && report.getImageUrl() != null && !report.getImageUrl().isEmpty()) {
                                Glide.with(this)
                                        .load(report.getImageUrl())
                                        .into(imageViewReport);
                            }

                            // Tambahkan setup untuk zoom dan enlarged image
                            setupImageClickListeners();
                            setupZoomFeature();

                            // Ambil nama pengirim
                            db.collection("users").document(String.valueOf(report.getUserId()))
                                    .get()
                                    .addOnSuccessListener(userDoc -> {
                                        if (userDoc.exists()) {
                                            String senderName = userDoc.getString("name");
                                            if (textViewSender != null) {
                                                textViewSender.setText("Pengirim: " + senderName);
                                            }
                                        }
                                    });

                            // Set status laporan jika ada
                            if (textViewStatus != null) {
                                String status = report.getStatus();
                                if (status != null) {
                                    switch (status) {
                                        case "pending":
                                            textViewStatus.setText("Status: Menunggu Persetujuan");
                                            break;
                                        case "approved":
                                            textViewStatus.setText("Status: Disetujui");
                                            textViewStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                                            // Sembunyikan tombol approve dan reject
                                            if (buttonApprove != null) buttonApprove.setVisibility(View.GONE);
                                            if (buttonReject != null) buttonReject.setVisibility(View.GONE);
                                            break;
                                        case "rejected":
                                            textViewStatus.setText("Status: Ditolak");
                                            textViewStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));

                                            // Tampilkan alasan penolakan jika ada
                                            TextView textViewRejectReason = findViewById(R.id.textViewRejectReason);
                                            if (textViewRejectReason != null) {
                                                String rejectReason = report.getRejectReason();
                                                if (rejectReason != null && !rejectReason.isEmpty()) {
                                                    textViewRejectReason.setText("Alasan Penolakan: " + rejectReason);
                                                    textViewRejectReason.setVisibility(View.VISIBLE);
                                                }
                                            }

                                            // Sembunyikan tombol approve dan reject
                                            if (buttonApprove != null) buttonApprove.setVisibility(View.GONE);
                                            if (buttonReject != null) buttonReject.setVisibility(View.GONE);
                                            break;
                                    }
                                }
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal mengambil detail laporan", Toast.LENGTH_SHORT).show();
                });
    }

    private void approveReport() {
        db.collection("reports").document(reportId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Report report = documentSnapshot.toObject(Report.class);

                        if (report != null) {
                            // Cari dokumen di processedReports
                            db.collection("processedReports")
                                    .whereEqualTo("reportId", reportId)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener(queryDocumentSnapshots -> {
                                        if (!queryDocumentSnapshots.isEmpty()) {
                                            // Dokumen sudah ada, update existing document
                                            DocumentSnapshot processedReportDoc = queryDocumentSnapshots.getDocuments().get(0);
                                            String processedReportDocId = processedReportDoc.getId();

                                            // Siapkan data update
                                            Map<String, Object> updateData = new HashMap<>();
                                            updateData.put("status", "approved");
                                            updateData.put("timestamp", FieldValue.serverTimestamp());

                                            // Update dokumen processedReports
                                            db.collection("processedReports")
                                                    .document(processedReportDocId)
                                                    .update(updateData)
                                                    .addOnSuccessListener(aVoid -> {
                                                        // Update status di reports
                                                        updateReportStatus(reportId, "approved");

                                                        // Kirim notifikasi ke pengguna
                                                        sendNotificationToUser(reportId, "approved", null);
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        Toast.makeText(this, "Gagal memperbarui status laporan", Toast.LENGTH_SHORT).show();
                                                    });
                                        } else {
                                            // Jika tidak ada dokumen, buat dokumen baru di processedReports
                                            createProcessedReport(report, "approved");

                                            // Kirim notifikasi ke pengguna
                                            sendNotificationToUser(reportId, "approved", null);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(this, "Gagal mencari dokumen", Toast.LENGTH_SHORT).show();
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal mengambil detail laporan", Toast.LENGTH_SHORT).show();
                });
    }


    private void createProcessedReport(Report report, String status) {
        Map<String, Object> processedReportData = new HashMap<>();
        processedReportData.put("reportId", report.getId());
        processedReportData.put("status", status);
        processedReportData.put("timestamp", FieldValue.serverTimestamp());

        db.collection("processedReports")
                .add(processedReportData)
                .addOnSuccessListener(documentReference -> {
                    // Update status di reports setelah dokumen processedReports dibuat
                    updateReportStatus(report.getId(), status);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal membuat dokumen processed report", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateReportStatus(String reportId, String status) {
        db.collection("reports")
                .document(reportId)
                .update("status", status)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Laporan berhasil disetujui", Toast.LENGTH_SHORT).show();
                    finish(); // Tutup aktivitas setelah berhasil
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal memperbarui status laporan", Toast.LENGTH_SHORT).show();
                });
    }

    private void rejectReport() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reject_reason, null);
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
                    Toast.makeText(this, "Alasan penolakan harus diisi", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Update status di Firestore untuk koleksi reports
                db.collection("reports").document(reportId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                Report report = documentSnapshot.toObject(Report.class);

                                if (report != null) {
                                    // Update status di Firestore untuk koleksi reports
                                    db.collection("reports")
                                            .document(reportId)
                                            .update("status", "rejected", "rejectReason", rejectReason)
                                            .addOnSuccessListener(aVoid -> {
                                                // Jika ada processedReportId, update juga di processedReports
                                                db.collection("reports").document(reportId)
                                                        .get()
                                                        .addOnSuccessListener(reportDoc -> {
                                                            if (reportDoc.exists()) {
                                                                String processedReportId = reportDoc.getString("processedReportId");
                                                                if (processedReportId != null) {
                                                                    updateProcessedReport(processedReportId, "rejected", rejectReason);
                                                                    // Kirim notifikasi ke pengguna
                                                                    sendNotificationToUser(reportId, "rejected", rejectReason);
                                                                } else {
                                                                    // Jika tidak ada processedReportId, buat dokumen baru
                                                                    createProcessedReportForRejectedReport(report, rejectReason);
                                                                }



                                                                Toast.makeText(this, "Laporan berhasil ditolak", Toast.LENGTH_SHORT).show();
                                                                dialog.dismiss(); // Tutup dialog setelah berhasil
                                                            }
                                                        });
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(this, "Gagal memperbarui status laporan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                            });
                                }
                            }
                        });
            });
        });

        dialog.show(); // Tampilkan dialog
    }


    private void createProcessedReportForRejectedReport(Report report, String rejectReason) {
        Map<String, Object> processedReportData = new HashMap<>();
        processedReportData.put("reportId", report.getId());
        processedReportData.put("status", "rejected");
        processedReportData.put("rejectReason", rejectReason);
        processedReportData.put("timestamp", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection("processedReports")
                .add(processedReportData)
                .addOnSuccessListener(documentReference -> {
                    Log.d("ReportRejection", "Processed report created for rejected report");
                })
                .addOnFailureListener(e -> {
                    Log.e("ReportRejection", "Failed to create processed report", e);
                });
    }


    private void sendNotificationToUser(String reportId, String status, String rejectReason) {
        db.collection("reports")
                .document(reportId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String userId = documentSnapshot.getString("userId");

                        Map<String, Object> notificationData = new HashMap<>();
                        notificationData.put("userId", userId);
                        notificationData.put("reportId", reportId);
                        notificationData.put("status", status);
                        notificationData.put("title", status.equals("approved") ?
                                "Laporan Disetujui" : "Laporan Ditolak");
                        notificationData.put("message", status.equals("approved") ?
                                "Laporan Anda telah disetujui" :
                                "Laporan Anda ditolak. Alasan: " + (rejectReason != null ? rejectReason : "Tidak ada alasan"));
                        notificationData.put("timestamp", FieldValue.serverTimestamp());
                        notificationData.put("isRead", false);

                        FirebaseFirestore.getInstance()
                                .collection("notifications")
                                .add(notificationData);
                    }
                });
    }
    private void updateProcessedReport(String processedReportDocId, String status, String rejectReason) {
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("status", status);
        if (rejectReason != null) {
            updateData.put("rejectReason", rejectReason);
        }
        updateData.put("timestamp", FieldValue.serverTimestamp());

        db.collection("processedReports")
                .document(processedReportDocId)
                .update(updateData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Status laporan berhasil diperbarui", Toast.LENGTH_SHORT).show();
                    finish(); // Kembali setelah berhasil
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal memperbarui status laporan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }


    private void setupImageClickListeners() {
        ImageView openEnlargedImage = findViewById(R.id.openEnlargedImage);
        ImageView closeEnlargedImage = findViewById(R.id.closeEnlargedImage);

        openEnlargedImage.setOnClickListener(v -> {
            if (report != null && report.getImageUrl() != null && !report.getImageUrl().isEmpty()) {
                enlargedImageContainer.setVisibility(View.VISIBLE);

                // Pastikan ImageView menggunakan matrix scale type
                imageViewEnlarged.setScaleType(ImageView.ScaleType.MATRIX);

                Glide.with(this)
                        .load(report.getImageUrl())
                        .into(new CustomTarget<Drawable>() {
                            @Override
                            public void onResourceReady(@NonNull Drawable drawable, @Nullable Transition<? super Drawable> transition) {
                                // Ukuran gambar asli
                                float imageWidth = drawable.getIntrinsicWidth();
                                float imageHeight = drawable.getIntrinsicHeight();

                                // Ukuran container
                                float containerWidth = enlargedImageContainer.getWidth();
                                float containerHeight = enlargedImageContainer.getHeight();

                                // Hitung scale untuk fit center
                                float scaleX = containerWidth / imageWidth;
                                float scaleY = containerHeight / imageHeight;
                                initialScale = Math.min(scaleX, scaleY);
                                scale = initialScale;

                                // Hitung posisi tengah
                                float scaledImageWidth = imageWidth * initialScale;
                                float scaledImageHeight = imageHeight * initialScale;
                                float translateX = (containerWidth - scaledImageWidth) / 2f;
                                float translateY = (containerHeight - scaledImageHeight) / 2f;

                                // Reset dan terapkan transformasi
                                matrix.reset();
                                matrix.postScale(initialScale, initialScale);
                                matrix.postTranslate(translateX, translateY);

                                // Set gambar dan matrix
                                imageViewEnlarged.setImageDrawable(drawable);
                                imageViewEnlarged.setImageMatrix(matrix);

                                // Simpan matrix awal
                                initialMatrix.set(matrix);
                            }

                            @Override
                            public void onLoadCleared(@Nullable Drawable placeholder) {}
                        });
            }
        });

        closeEnlargedImage.setOnClickListener(v -> {
            enlargedImageContainer.setVisibility(View.GONE);
            resetZoom();
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupZoomFeature() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                float newScale = scale * scaleFactor;

                // Batasi zoom antara ukuran awal dan 3x zoom
                if (newScale >= initialScale && newScale <= initialScale * 5.0f) {
                    scale = newScale;
                    float focusX = detector.getFocusX();
                    float focusY = detector.getFocusY();
                    matrix.postScale(scaleFactor, scaleFactor, focusX, focusY);
                    imageViewEnlarged.setImageMatrix(matrix);
                }
                return true;
            }
        });

        imageViewEnlarged.setOnTouchListener(new View.OnTouchListener() {
            private float lastX, lastY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scaleGestureDetector.onTouchEvent(event);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getX();
                        lastY = event.getY();
                        mode = DRAG;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (mode == DRAG) {
                            float deltaX = event.getX() - lastX;
                            float deltaY = event.getY() - lastY;

                            // Terapkan translasi dengan batasan
                            float[] values = new float[9];
                            matrix.getValues(values);
                            float transX = values[Matrix.MTRANS_X];
                            float transY = values[Matrix.MTRANS_Y];
                            float scaleX = values[Matrix.MSCALE_X];
                            float scaleY = values[Matrix.MSCALE_Y];

                            float imageWidth = imageViewEnlarged.getDrawable().getIntrinsicWidth() * scaleX;
                            float imageHeight = imageViewEnlarged.getDrawable().getIntrinsicHeight() * scaleY;
                            float viewWidth = imageViewEnlarged.getWidth();
                            float viewHeight = imageViewEnlarged.getHeight();

                            // Batasi pergeseran horizontal
                            if (imageWidth > viewWidth) {
                                if (transX + deltaX > 0) deltaX = -transX;
                                else if (transX + deltaX < viewWidth - imageWidth)
                                    deltaX = viewWidth - imageWidth - transX;
                            } else {
                                deltaX = 0;
                            }

                            // Batasi pergeseran vertikal
                            if (imageHeight > viewHeight) {
                                if (transY + deltaY > 0) deltaY = -transY;
                                else if (transY + deltaY < viewHeight - imageHeight)
                                    deltaY = viewHeight - imageHeight - transY;
                            } else {
                                deltaY = 0;
                            }

                            // Terapkan translasi
                            matrix.postTranslate(deltaX, deltaY);
                            imageViewEnlarged.setImageMatrix(matrix);

                            lastX = event.getX();
                            lastY = event.getY();
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        mode = NONE;
                        break;
                }
                return true;
            }
        });
    }

    private void resetZoom() {
        scale = initialScale;
        matrix.set(initialMatrix);
        imageViewEnlarged.setImageMatrix(matrix);
        mode = NONE;
    }
}


