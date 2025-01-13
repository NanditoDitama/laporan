package com.example.laporan2;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import android.graphics.Matrix;
import android.view.ScaleGestureDetector;
import android.view.MotionEvent;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;


public class EditedReportDetailActivity extends AppCompatActivity {
    private static final String TAG = "EditedReportDetailActivity";


    private View enlargedImageContainer;
    private ImageView imageViewEnlarged, openEnlargedImageOriginal, openEnlargedImageEdited, closeEnlargedImage;
    private Matrix matrix = new Matrix();
    private Matrix initialMatrix = new Matrix();
    private float scale, initialScale;
    private ScaleGestureDetector scaleGestureDetector;

    // Konstanta untuk mode sentuhan
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private int mode = NONE;


    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String reportId;
    private Map<String, Object> originalData;
    private Map<String, Object> editedData;

    // UI Components
    private TextView textViewOriginalTitle, textViewEditedTitle;
    private TextView textViewOriginalDescription, textViewEditedDescription;
    private TextView textViewOriginalAmount, textViewEditedAmount;
    private TextView textViewOriginalDate, textViewEditedDate;
    private ImageView imageViewOriginal, imageViewEdited;
    private Button buttonApprove, buttonReject;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edited_report_detail);

        enlargedImageContainer = findViewById(R.id.enlargedImageContainer);
        imageViewEnlarged = findViewById(R.id.imageViewEnlarged);
        openEnlargedImageOriginal = findViewById(R.id.openEnlargedImageOriginal);
        openEnlargedImageEdited = findViewById(R.id.openEnlargedImageEdited);
        closeEnlargedImage = findViewById(R.id.closeEnlargedImage);

        setupImageClickListeners();
        setupZoomFeature();


        // Inisialisasi Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Inisialisasi View
        initializeViews();

        // Dapatkan ID laporan dari intent
        reportId = getIntent().getStringExtra("reportId");

        // Muat detail laporan
        loadReportDetails();
    }

    private void initializeViews() {
        // Inisialisasi semua view seperti sebelumnya
        textViewOriginalTitle = findViewById(R.id.textViewOriginalTitle);
        textViewOriginalDescription = findViewById(R.id.textViewOriginalDescription);
        textViewOriginalAmount = findViewById(R.id.textViewOriginalAmount);
        textViewOriginalDate = findViewById(R.id.textViewOriginalDate);
        imageViewOriginal = findViewById(R.id.imageViewOriginal);


        textViewEditedTitle = findViewById(R.id.textViewEditedTitle);
        textViewEditedDescription = findViewById(R.id.textViewEditedDescription);
        textViewEditedAmount = findViewById(R.id.textViewEditedAmount);
        textViewEditedDate = findViewById(R.id.textViewEditedDate);
        imageViewEdited = findViewById(R.id.imageViewEdited);

        buttonApprove = findViewById(R.id.buttonApprove);
        buttonReject = findViewById(R.id.buttonReject);

        buttonApprove.setOnClickListener(v -> approveEdit());
        buttonReject.setOnClickListener(v -> rejectEdit());
    }

    private void loadReportDetails() {
        db.collection("editedReports").document(reportId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        originalData = (Map<String, Object>) documentSnapshot.get("originalData");
                        editedData = (Map<String, Object>) documentSnapshot.get("editedData");

                        if (originalData != null && editedData != null) {
                            setOriginalData();
                            setEditedData();
                        } else {
                            Toast.makeText(this, "Data tidak lengkap", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Dokumen tidak ditemukan", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Gagal memuat detail laporan", e);
                    Toast.makeText(this, "Gagal memuat detail: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void approveEdit() {
        db.collection("editedReports").document(reportId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String originalReportId = documentSnapshot.getString("originalReportId");
                        Map<String, Object> editedData = (Map<String, Object>) documentSnapshot.get("editedData");

                        if (originalReportId == null || editedData == null) {
                            Toast.makeText(this, "Data laporan tidak valid", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Ekstrak userId dari berbagai sumber
                        String userId = extractUserId(documentSnapshot);

                        // Kirim notifikasi approval
                        sendApprovalNotification(userId, originalReportId);

                        // Proses update laporan
                        processApprovedReport(originalReportId, editedData);
                    } else {
                        Toast.makeText(this, "Dokumen edit tidak ditemukan", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal mengambil dokumen edit", Toast.LENGTH_SHORT).show();
                });
    }

    private void processApprovedReport(String originalReportId, Map<String, Object> editedData) {
        Map<String, Object> updateData = new HashMap<>(editedData);
        updateData.put("status", "approved");
        updateData.put("timestamp", FieldValue.serverTimestamp());

        // Cek apakah laporan sudah ada di processedReports
        db.collection("processedReports")
                .whereEqualTo("reportId", originalReportId)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // Dokumen sudah ada, update dokumen yang sudah ada
                        DocumentSnapshot existingDoc = queryDocumentSnapshots.getDocuments().get(0);
                        String existingDocId = existingDoc.getId();

                        db.collection("processedReports")
                                .document(existingDocId)
                                .update(updateData)
                                .addOnSuccessListener(aVoid -> {
                                    // Update laporan asli
                                    updateOriginalReport(originalReportId, updateData);
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Gagal memperbarui dokumen di processedReports", Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        // Jika dokumen belum ada, buat dokumen baru
                        createProcessedReport(originalReportId, updateData);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal memeriksa processedReports", Toast.LENGTH_SHORT).show();
                });
    }


    private void updateOriginalReport(String originalReportId, Map<String, Object> updateData) {
        db.collection("reports").document(originalReportId)
                .update(updateData)
                .addOnSuccessListener(aVoid -> {
                    // Hapus dokumen editedReports
                    db.collection("editedReports").document(reportId)
                            .delete()
                            .addOnSuccessListener(deleteVoid -> {
                                Toast.makeText(this, "Laporan berhasil diperbarui", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(deleteE -> {
                                Toast.makeText(this, "Gagal menghapus dokumen edit", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal memperbarui laporan asli", Toast.LENGTH_SHORT).show();
                });
    }

    private void createProcessedReport(String originalReportId, Map<String, Object> updateData) {
        Map<String, Object> processedReportData = new HashMap<>(updateData);
        processedReportData.put("reportId", originalReportId);
        processedReportData.put("timestamp", FieldValue.serverTimestamp());

        db.collection("processedReports")
                .add(processedReportData)
                .addOnSuccessListener(documentReference -> {
                    // Update laporan asli
                    updateOriginalReport(originalReportId, updateData);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal membuat dokumen processed report", Toast.LENGTH_SHORT).show();
                });
    }


    private void rejectEdit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reject_reason, null);
        EditText editTextReason = dialogView.findViewById(R.id.editTextRejectReason);

        builder.setTitle("Alasan Penolakan")
                .setView(dialogView)
                .setPositiveButton("Tolak", (dialog, which) -> {
                    String rejectReason = editTextReason.getText().toString().trim();

                    if (rejectReason.isEmpty()) {
                        Toast.makeText(this, "Alasan penolakan harus diisi", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    db.collection("editedReports").document(reportId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    String originalReportId = documentSnapshot.getString("originalReportId");
                                    Map<String, Object> editedData = (Map<String, Object>) documentSnapshot.get("editedData");

                                    if (originalReportId == null || editedData == null) {
                                        Toast.makeText(this, "Data laporan tidak valid", Toast.LENGTH_SHORT).show();
                                        return;
                                    }

                                    // Kirim notifikasi penolakan
                                    String userId = extractUserId(documentSnapshot);
                                    sendRejectionNotification(userId, editedData, rejectReason);

                                    // Tambahkan status rejected dan alasan penolakan
                                    Map<String, Object> updateData = new HashMap<>(editedData);
                                    updateData.put("status", "rejected");
                                    updateData.put("rejectReason", rejectReason);
                                    updateData.put("timestamp", FieldValue.serverTimestamp());

                                    // Proses update laporan
                                    processRejectedReport(originalReportId, updateData);
                                } else {
                                    Toast.makeText(this, "Dokumen edit tidak ditemukan", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Gagal mengambil dokumen edit", Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void processRejectedReport(String originalReportId, Map<String, Object> editedData) {
        Map<String, Object> updateData = new HashMap<>(editedData);
        updateData.put("status", "rejected");
        updateData.put("timestamp", FieldValue.serverTimestamp());

        // Cek apakah laporan sudah ada di processedReports
        db.collection("processedReports")
                .whereEqualTo("reportId", originalReportId)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // Dokumen sudah ada, update dokumen yang sudah ada
                        DocumentSnapshot existingDoc = queryDocumentSnapshots.getDocuments().get(0);
                        String existingDocId = existingDoc.getId();

                        db.collection("processedReports")
                                .document(existingDocId)
                                .update(updateData)
                                .addOnSuccessListener(aVoid -> {
                                    // Update laporan asli
                                    updateRejectedReport(originalReportId, updateData);
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Gagal memperbarui dokumen di processedReports", Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        // Jika dokumen belum ada, buat dokumen baru
                        createRejectedProcessedReport(originalReportId, updateData);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal memeriksa processedReports", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateRejectedReport(String originalReportId, Map<String, Object> updateData) {
        db.collection("reports").document(originalReportId)
                .update(updateData)
                .addOnSuccessListener(aVoid -> {
                    // Hapus dokumen editedReports
                    db.collection("editedReports").document(reportId)
                            .delete()
                            .addOnSuccessListener(deleteVoid -> {
                                Toast.makeText(this, "Laporan edit ditolak", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(deleteE -> {
                                Toast.makeText(this, "Gagal menghapus dokumen edit", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal memperbarui laporan asli", Toast.LENGTH_SHORT).show();
                });
    }

    private void createRejectedProcessedReport(String originalReportId, Map<String, Object> updateData) {
        Map<String, Object> processedReportData = new HashMap<>(updateData);
        processedReportData.put("reportId", originalReportId);
        processedReportData.put("status", "rejected");
        processedReportData.put("timestamp", FieldValue.serverTimestamp());

        db.collection("processedReports")
                .add(processedReportData)
                .addOnSuccessListener(documentReference -> {
                    // Update laporan asli
                    updateRejectedReport(originalReportId, processedReportData);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal membuat dokumen processed report", Toast.LENGTH_SHORT).show();
                });
    }

    private String extractUserId(DocumentSnapshot documentSnapshot) {
        String userId = null;
        if (documentSnapshot.contains("userId")) {
            userId = documentSnapshot.getString("userId");
        }
        if (userId == null && originalData != null) {
            userId = (String) originalData.get("userId");
        }
        return userId;
    }

    private void sendApprovalNotification(String userId, String originalReportId) {
        if (userId == null) {
            Log.e(TAG, "User  ID is null, cannot send approval notification");
            return;
        }

        String title = "Pengajuan Edit Disetujui";
        String message = "Pengajuan edit laporan Anda telah disetujui.";

        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", userId);
        notification.put("reportId", originalReportId);
        notification.put("title", title);
        notification.put("message", message);
        notification.put("timestamp", FieldValue.serverTimestamp());
        notification.put("isRead", false);
        notification.put("type", "editApproved");

        db.collection("notifications")
                .add(notification)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Approval notification sent successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send approval notification", e);
                });
    }

    private void sendRejectionNotification(String userId, Map<String, Object> editedData, String rejectReason) {
        if (userId == null) {
            Log.e(TAG, "User  ID is null, cannot send rejection notification");
            return;
        }

        String title = "Pengajuan Edit Ditolak";
        String message = "Pengajuan edit laporan Anda ditolak. Alasan: " + rejectReason;

        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", userId);
        notification.put("reportId", editedData.get("originalReportId"));
        notification.put("title", title);
        notification.put("message", message);
        notification.put("timestamp", FieldValue.serverTimestamp());
        notification.put("isRead", false);
        notification.put("type", "editRejected");

        db.collection("notifications")
                .add(notification)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Rejection notification sent successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send rejection notification", e);
                });
    }

    private void setOriginalData() {
        textViewOriginalTitle.setText(safeGetString(originalData, "title"));
        textViewOriginalDescription.setText(safeGetString(originalData, "description"));
        Double originalAmount = safeGetDouble(originalData, "amount");
        textViewOriginalAmount.setText(originalAmount != null ?
                NumberFormat.getCurrencyInstance().format(originalAmount) : "Rp 0");
        Date originalDate = safeGetDate(originalData, "date");
        textViewOriginalDate.setText(originalDate != null ?
                new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(originalDate) : "Tidak ada tanggal");
        String originalImageUrl = safeGetString(originalData, "imageUrl");
        if (originalImageUrl != null && !originalImageUrl.isEmpty()) {
            Glide.with(this)
                    .load(originalImageUrl)
                    .into(imageViewOriginal);
        }
    }

    private void setEditedData() {
        textViewEditedTitle.setText(safeGetString(editedData, "title"));
        textViewEditedDescription.setText(safeGetString(editedData, "description"));
        Double editedAmount = safeGetDouble(editedData, "amount");
        textViewEditedAmount.setText(editedAmount != null ?
                NumberFormat.getCurrencyInstance().format(editedAmount) : "Rp 0");
        Date editedDate = safeGetDate(editedData, "date");
        textViewEditedDate.setText(editedDate != null ?
                new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(editedDate) : "Tidak ada tanggal");
        String editedImageUrl = safeGetString(editedData, "imageUrl");
        if (editedImageUrl != null && !editedImageUrl.isEmpty()) {
            Glide.with(this)
                    .load(editedImageUrl)
                    .into(imageViewEdited);
        }
    }

    private String safeGetString(Map<String, Object> data, String key) {
        if (data != null && data.containsKey(key)) {
            Object value = data.get(key);
            return value != null ? value.toString() : "Tidak ada data";
        }
        return "Tidak ada data";
    }

    private Double safeGetDouble(Map<String, Object> data, String key) {
        if (data != null && data.containsKey(key)) {
            Object value = data.get(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        }
        return null;
    }

    private Date safeGetDate(Map<String, Object> data, String key) {
        if (data != null && data.containsKey(key)) {
            Object value = data.get(key);
            if (value instanceof Timestamp) {
                return ((Timestamp) value).toDate();
            } else if (value instanceof Date) {
                return (Date) value;
            }
        }
        return null;
    }


    private void setupImageClickListeners() {
        // Untuk gambar asli
        openEnlargedImageOriginal.setOnClickListener(v -> {
            String originalImageUrl = safeGetString(originalData, "imageUrl");
            if (originalImageUrl != null && !originalImageUrl.isEmpty()) {
                enlargeImage(originalImageUrl);
            }
        });

        // Untuk gambar yang diedit
        openEnlargedImageEdited.setOnClickListener(v -> {
            String editedImageUrl = safeGetString(editedData, "imageUrl");
            if (editedImageUrl != null && !editedImageUrl.isEmpty()) {
                enlargeImage(editedImageUrl);
            }
        });

        closeEnlargedImage.setOnClickListener(v -> {
            enlargedImageContainer.setVisibility(View.GONE);
            resetZoom();
        });
    }

    private void enlargeImage(String imageUrl) {
        // Pastikan container sudah siap
        enlargedImageContainer.post(() -> {
            enlargedImageContainer.setVisibility(View.VISIBLE);
            imageViewEnlarged.setScaleType(ImageView.ScaleType.MATRIX);

            Glide.with(this)
                    .load(imageUrl)
                    .into(new CustomTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable drawable, @Nullable Transition<? super Drawable> transition) {
                            setupImageZoom(drawable);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            Toast.makeText(EditedReportDetailActivity.this,
                                    "Gagal memuat gambar", Toast.LENGTH_SHORT).show();
                            enlargedImageContainer.setVisibility(View.GONE);
                        }
                    });
        });
    }

    private void setupImageZoom(Drawable drawable) {
        if (drawable == null) {
            Log.e(TAG, "Drawable is null");
            Toast.makeText(this, "Gagal memuat gambar", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Ukuran gambar asli
            float imageWidth = drawable.getIntrinsicWidth();
            float imageHeight = drawable.getIntrinsicHeight();

            // Ukuran container
            float containerWidth = enlargedImageContainer.getWidth();
            float containerHeight = enlargedImageContainer.getHeight();

            // Log untuk debugging
            Log.d(TAG, "Image dimensions: " + imageWidth + "x" + imageHeight);
            Log.d(TAG, "Container dimensions: " + containerWidth + "x" + containerHeight);

            // Cegah pembagian dengan nol
            if (containerWidth <= 0 || containerHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
                Log.e(TAG, "Invalid dimensions");
                return;
            }

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

        } catch (Exception e) {
            Log.e(TAG, "Error in setupImageZoom", e);
            Toast.makeText(this, "Gagal memproses gambar", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupZoomFeature() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (imageViewEnlarged.getDrawable() == null) return true;

                float scaleFactor = detector.getScaleFactor();
                float newScale = scale * scaleFactor;

                // Batasi zoom antara ukuran awal dan 5x zoom
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

                            Drawable drawable = imageViewEnlarged.getDrawable();
                            if (drawable == null) return true;

                            float imageWidth = drawable.getIntrinsicWidth() * scaleX;
                            float imageHeight = drawable.getIntrinsicHeight() * scaleY;
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