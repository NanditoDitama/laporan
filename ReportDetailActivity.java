package com.example.laporan2;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;


import android.view.WindowManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;



import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import android.content.Intent;
import android.graphics.Matrix;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;


public class ReportDetailActivity extends AppCompatActivity {
    private ImageView imageViewReport;
    private TextView textViewTitle, textViewDescription, textViewDate, textViewAmount;
    private ProgressBar progressBar, imageProgressBar;
    private ImageView imageViewEnlarged;
    private FrameLayout enlargedImageContainer;
    private FirebaseFirestore db;
    private Report report;
    private Button buttonPaidStatus;
    private Matrix matrix = new Matrix();
    private float scale = 1f;
    private ScaleGestureDetector scaleGestureDetector;
    private float lastTouchX, lastTouchY;
    private float posX, posY;
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;


    private Matrix initialMatrix = new Matrix();
    private float initialScale = 1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_detail);



        initializeViews();
        db = FirebaseFirestore.getInstance();

        String reportId = getIntent().getStringExtra("reportId");
        if (reportId != null) {
            loadReportData(reportId);
        } else {
            Toast.makeText(this, "Report ID not available", Toast.LENGTH_SHORT).show();
            finish();
        }
        setupImageClickListeners();
        setupZoomFeature();
        setupImageClickListeners();
    }


    private void initializeViews() {
        imageViewReport = findViewById(R.id.imageViewReport);
        imageViewEnlarged = findViewById(R.id.imageViewEnlarged);
        textViewTitle = findViewById(R.id.textViewTitle);
        textViewDescription = findViewById(R.id.textViewDescription);
        textViewDate = findViewById(R.id.textViewDate);
        textViewAmount = findViewById(R.id.textViewAmount);
        progressBar = findViewById(R.id.progressBar);
        enlargedImageContainer = findViewById(R.id.enlargedImageContainer);
        buttonPaidStatus = findViewById(R.id.buttonPaidStatus);
    }

    private void loadReportData(String reportId) {
        // Periksa null sebelum menggunakan progressBar
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        // Nonaktifkan interaksi pengguna
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

        db.collection("reports").document(reportId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    // Gunakan runOnUiThread untuk operasi UI
                    runOnUiThread(() -> {
                        // Periksa null sebelum menyembunyikan
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }

                        // Aktifkan kembali interaksi pengguna
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

                        // Proses data
                        updateUI(documentSnapshot);
                    });
                })
                .addOnFailureListener(e -> {
                    // Gunakan runOnUiThread untuk operasi UI
                    runOnUiThread(() -> {
                        // Periksa null sebelum menyembunyikan
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }

                        // Aktifkan kembali interaksi pengguna
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

                        // Tampilkan pesan error
                        Toast.makeText(ReportDetailActivity.this,
                                "Error loading report: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        finish();
                    });
                });
    }
    private void showShareDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_share_report, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Bagikan Laporan");

        EditText inputSearch = dialogView.findViewById(R.id.inputSearch);
        TextView textViewSearchResult = dialogView.findViewById(R.id.textViewSearchResult);
        RadioGroup radioGroupResults = dialogView.findViewById(R.id.radioGroupResults);

        builder.setView(dialogView);
        builder.setPositiveButton("Bagikan", null);
        builder.setNegativeButton("Batal", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();

        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String searchText = s.toString().trim();
                if (searchText.length() >= 3) {
                    searchUsers(searchText, textViewSearchResult, radioGroupResults);
                } else {
                    textViewSearchResult.setVisibility(View.GONE);
                    radioGroupResults.setVisibility(View.GONE);
                    radioGroupResults.removeAllViews();
                }
            }
        });

        dialog.setOnShowListener(dialogInterface -> {
            Button buttonPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            buttonPositive.setOnClickListener(view -> {
                int selectedId = radioGroupResults.getCheckedRadioButtonId();
                if (selectedId == -1) {
                    Toast.makeText(ReportDetailActivity.this,
                            "Pilih pengguna terlebih dahulu", Toast.LENGTH_SHORT).show();
                    return;
                }

                RadioButton selectedRadioButton = radioGroupResults.findViewById(selectedId);
                String userId = (String) selectedRadioButton.getTag();
                String userName = selectedRadioButton.getText().toString().split("\\(")[0].trim();
                shareReport(userId, userName, dialog);
            });
        });

        dialog.show();
    }



    private void searchUsers(String searchText, TextView textViewSearchResult, RadioGroup radioGroupResults) {
        db.collection("users")
                .whereArrayContains("searchTerms", searchText.toLowerCase())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    radioGroupResults.removeAllViews();

                    if (queryDocumentSnapshots.isEmpty()) {
                        textViewSearchResult.setText("Tidak ada hasil ditemukan");
                        textViewSearchResult.setVisibility(View.VISIBLE);
                        radioGroupResults.setVisibility(View.GONE);
                        return;
                    }

                    textViewSearchResult.setVisibility(View.GONE);
                    radioGroupResults.setVisibility(View.VISIBLE);

                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        String name = document.getString("name");
                        String email = document.getString("email");
                        String userId = document.getId();

                        RadioButton radioButton = new RadioButton(this);
                        radioButton.setText(name + " (" + email + ")");
                        radioButton.setTag(userId);
                        radioGroupResults.addView(radioButton);
                    }
                })
                .addOnFailureListener(e -> {
                    textViewSearchResult.setText("Gagal melakukan pencarian");
                    textViewSearchResult.setVisibility(View.VISIBLE);
                    radioGroupResults.setVisibility(View.GONE);
                });
    }


    private void shareReport(String recipientId, String recipientName, AlertDialog dialog) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String currentUserName = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
        String sharedReportId = UUID.randomUUID().toString();

        Map<String, Object> sharedReport = new HashMap<>();
        sharedReport.put("reportId", report.getId());
        sharedReport.put("senderId", currentUserId);
        sharedReport.put("senderName", currentUserName);
        sharedReport.put("recipientId", recipientId);
        sharedReport.put("recipientName", recipientName);
        sharedReport.put("sharedDate", new Date());
        sharedReport.put("timestamp", FieldValue.serverTimestamp());
        sharedReport.put("isReadRecipient", false);
        sharedReport.put("isReadSender", false);
        sharedReport.put("deletedBySender", false);
        sharedReport.put("deletedByRecipient", false);

        db.collection("sharedReports")
                .document(sharedReportId)
                .set(sharedReport)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Laporan berhasil dibagikan", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal membagikan laporan: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }


    @SuppressLint("SetTextI18n")
    private void updateUI(DocumentSnapshot document) {
        progressBar.setVisibility(View.GONE);

        if (document.exists()) {
            report = document.toObject(Report.class);
            if (report != null) {



                // Set gambar dengan ProgressBar
                if (report.getImageUrl() != null && !report.getImageUrl().isEmpty()) {
                    // Tampilkan ProgressBar gambar jika tidak null
                    if (imageProgressBar != null) {
                        imageProgressBar.setVisibility(View.VISIBLE);
                    }

                    Glide.with(this)
                            .load(report.getImageUrl())
                            .listener(new RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e,
                                                            Object model,
                                                            Target<Drawable> target,
                                                            boolean isFirstResource) {
                                    // Sembunyikan ProgressBar jika gagal
                                    runOnUiThread(() -> {
                                        if (imageProgressBar != null) {
                                            imageProgressBar.setVisibility(View.GONE);
                                        }
                                        imageViewReport.setVisibility(View.GONE);
                                    });
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(Drawable resource,
                                                               Object model,
                                                               Target<Drawable> target,
                                                               DataSource dataSource,
                                                               boolean isFirstResource) {
                                    // Sembunyikan ProgressBar saat gambar siap
                                    runOnUiThread(() -> {
                                        if (imageProgressBar != null) {
                                            imageProgressBar.setVisibility(View.GONE);
                                        }
                                        imageViewReport.setVisibility(View.VISIBLE);
                                    });
                                    return false;
                                }
                            })
                            .into(imageViewReport);
                } else {
                    // Sembunyikan ImageView jika tidak ada gambar
                    imageViewReport.setVisibility(View.GONE);

                    // Sembunyikan ProgressBar gambar jika ada
                    if (imageProgressBar != null) {
                        imageProgressBar.setVisibility(View.GONE);
                    }
                }











                // Set judul dan deskripsi
                textViewTitle.setText(report.getTitle());
                textViewDescription.setText(report.getDescription());

                // Format dan set tanggal
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
                textViewDate.setText(dateFormat.format(report.getDate()));

                // Format dan set jumlah
                NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
                textViewAmount.setText(currencyFormat.format(report.getAmount()));

                Button buttonPaidStatus = findViewById(R.id.buttonPaidStatus);

                // Periksa apakah field isPaid ada dan bernilai true
                Boolean isPaid = document.getBoolean("isPaid");
                if (Boolean.TRUE.equals(isPaid)) {
                    buttonPaidStatus.setVisibility(View.VISIBLE);
                    buttonPaidStatus.setOnClickListener(v -> {
                        // Buat intent untuk halaman detail pembayaran
                        Intent intent = new Intent(this, PaymentProofDetailActivity.class);
                        intent.putExtra("reportId", report.getId());
                        startActivity(intent);
                    });
                } else {
                    buttonPaidStatus.setVisibility(View.GONE);
                }


                // Set gambar jika ada
                if (report.getImageUrl() != null && !report.getImageUrl().isEmpty()) {
                    Glide.with(this)
                            .load(report.getImageUrl())
                            .into(imageViewReport);
                    imageViewReport.setVisibility(View.VISIBLE);
                } else {
                    imageViewReport.setVisibility(View.GONE);
                }
                TextView textViewRejectReason = findViewById(R.id.textViewRejectReason);
                if ("rejected".equals(report.getStatus())) {
                    textViewRejectReason.setVisibility(View.VISIBLE);
                    textViewRejectReason.setText("Alasan Penolakan: " +
                            (report.getRejectReason() != null ? report.getRejectReason() : "Tidak ada alasan"));
                } else {
                    textViewRejectReason.setVisibility(View.GONE);
                }
            }
        } else {
            Toast.makeText(this, "Report not found", Toast.LENGTH_SHORT).show();
            finish();
        }
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
    private void updateReadStatus(String reportId) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("sharedReports")
                .whereEqualTo("reportId", reportId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        boolean isSender = currentUserId.equals(document.getString("senderId"));
                        String fieldToUpdate = isSender ? "isReadSender" : "isReadRecipient";

                        document.getReference().update(fieldToUpdate, true)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d("ReportDetail", "Read status updated successfully");
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("ReportDetail", "Error updating read status", e);
                                });
                    }
                });
    }
}
