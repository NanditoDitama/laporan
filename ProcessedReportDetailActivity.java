package com.example.laporan2;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ProcessedReportDetailActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PAYMENT = 1001;
    private TextView textViewTitle, textViewSender, textViewDate,
            textViewAmount, textViewDescription,
            textViewStatus, textViewRejectReason;
    private ImageView imageViewReport;
    private FirebaseFirestore db;
    private Button iconPaidStatus;
    private String reportId;



    private View enlargedImageContainer;
    private ImageView imageViewEnlarged, openEnlargedImage, closeEnlargedImage;
    private Matrix matrix = new Matrix();
    private Matrix initialMatrix = new Matrix();
    private float scale, initialScale;
    private ScaleGestureDetector scaleGestureDetector;
    // Konstanta untuk mode sentuhan
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private int mode = NONE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processed_report_detail);

        enlargedImageContainer = findViewById(R.id.enlargedImageContainer);
        imageViewEnlarged = findViewById(R.id.imageViewEnlarged);
        openEnlargedImage = findViewById(R.id.openEnlargedImage);
        closeEnlargedImage = findViewById(R.id.closeEnlargedImage);

        // Panggil metode setup
        setupImageClickListeners();
        setupZoomFeature();



        // Inisialisasi Firebase Firestore
        db = FirebaseFirestore.getInstance();

        // Inisialisasi views
        initializeViews();

        // Ambil reportId dari intent
        reportId = getIntent().getStringExtra("reportId");

        if (reportId != null) {
            // Muat detail laporan dari koleksi processedReports
            loadProcessedReportDetails();
        } else {
            Toast.makeText(this, "ID Laporan tidak tersedia", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initializeViews() {
        textViewTitle = findViewById(R.id.textViewTitle);
        textViewSender = findViewById(R.id.textViewSender);
        textViewDate = findViewById(R.id.textViewDate);
        textViewAmount = findViewById(R.id.textViewAmount);
        textViewDescription = findViewById(R.id.textViewDescription);
        textViewStatus = findViewById(R.id.textViewStatus);
        textViewRejectReason = findViewById(R.id.textViewRejectReason);
        imageViewReport = findViewById(R.id.imageViewReport);
        iconPaidStatus = findViewById(R.id.iconPaidStatus);
    }


    private void setupImageClickListeners() {
        openEnlargedImage.setOnClickListener(v -> {
            if (imageViewReport.getDrawable() != null) {
                enlargedImageContainer.setVisibility(View.VISIBLE);

                // Pastikan ImageView menggunakan matrix scale type
                imageViewEnlarged.setScaleType(ImageView.ScaleType.MATRIX);

                Glide.with(this)
                        .load(imageViewReport.getTag()) // Gunakan tag untuk menyimpan URL gambar
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







    @SuppressLint("SetTextI18n")
    private void loadProcessedReportDetails() {
        db.collection("processedReports")
                .document(reportId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Ambil data laporan
                        String title = documentSnapshot.getString("title");
                        String description = documentSnapshot.getString("description");
                        Double amount = documentSnapshot.getDouble("amount");
                        Date date = documentSnapshot.getDate("date");
                        String status = documentSnapshot.getString("status");
                        String imageUrl = documentSnapshot.getString("imageUrl");
                        String rejectReason = documentSnapshot.getString("rejectReason");
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(imageUrl)
                                    .into(imageViewReport);
                            imageViewReport.setTag(imageUrl);
                            imageViewReport.setVisibility(View.VISIBLE);
                            openEnlargedImage.setVisibility(View.VISIBLE);
                        } else {
                            imageViewReport.setVisibility(View.GONE);
                            openEnlargedImage.setVisibility(View.GONE);
                        }

                        Boolean isPaid = documentSnapshot.getBoolean("isPaid");
                        if (Boolean.TRUE.equals(isPaid)) {
                            iconPaidStatus.setVisibility(View.VISIBLE);
                            iconPaidStatus.setOnClickListener(v -> {
                                // Buka halaman detail pembayaran
                                Intent intent = new Intent(this, PaymentProofDetailActivity.class);
                                intent.putExtra("reportId", reportId);
                                startActivity(intent);
                            });
                        } else {
                            iconPaidStatus.setVisibility(View.VISIBLE);
                            iconPaidStatus.setText("Belum Dibayar");

                            iconPaidStatus.setOnClickListener(v -> {
                                // Buat dialog konfirmasi
                                new AlertDialog.Builder(this)
                                        .setTitle("Konfirmasi Pembayaran")
                                        .setMessage("Apakah Anda ingin melanjutkan proses pembayaran untuk laporan ini?")
                                        .setPositiveButton("Ya, Bayar", (dialog, which) -> {
                                            // Buat ArrayList untuk menampung Report
                                            ArrayList<Report> reportsToTransaksi = new ArrayList<>();

                                            // Buat objek Report dari dokumen saat ini
                                            Report report = new Report();
                                            report.setId(reportId);

                                            // Gunakan ID dokumen processedReports sebagai processedReportId
                                            report.setProcessedReportId(reportId);

                                            // Set title
                                            String reportTitle = documentSnapshot.getString("title");
                                            report.setTitle(reportTitle != null ? reportTitle : "Laporan Tanpa Judul");

                                            // Set amount
                                            Double reportAmount = documentSnapshot.getDouble("amount");
                                            report.setAmount(reportAmount != null ? reportAmount : 0.0);

                                            // Set userId (opsional, tapi disarankan)
                                            String userId = documentSnapshot.getString("userId");
                                            report.setUserId(userId);

                                            // Tambahkan ke list
                                            reportsToTransaksi.add(report);

                                            // Buat intent untuk TransaksiActivity
                                            Intent intent = new Intent(this, TransaksiActivity.class);

                                            // Kirim list Report
                                            intent.putParcelableArrayListExtra("filteredReports", reportsToTransaksi);

                                            // Gunakan startActivityForResult untuk mendapatkan hasil
                                            startActivityForResult(intent, REQUEST_CODE_PAYMENT);
                                        })
                                        .setNegativeButton("Batal", null)
                                        .create()
                                        .show();
                            });
                        }



                        // Perbaikan pada bagian senderName
                        String senderName = documentSnapshot.getString("senderName");
                        if (senderName == null || senderName.isEmpty()) {
                            // Jika senderName null atau kosong, cari di collection users
                            db.collection("users")
                                    .document(documentSnapshot.getString("userId"))
                                    .get()
                                    .addOnSuccessListener(userDoc -> {
                                        String retrievedName = userDoc.getString("name");
                                        textViewSender.setText("Pengirim: " +
                                                (retrievedName != null ? retrievedName : "Tidak diketahui"));
                                    })
                                    .addOnFailureListener(e -> {
                                        textViewSender.setText("Pengirim: Tidak diketahui");
                                    });
                        } else {
                            textViewSender.setText("Pengirim: " + senderName);
                        }

                        // Set data ke views (sisanya tetap sama)
                        textViewTitle.setText(title);
                        textViewDescription.setText(description);

                        // Format jumlah dengan mata uang
                        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
                        textViewAmount.setText(currencyFormat.format(amount != null ? amount : 0));

                        // Format tanggal
                        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
                        textViewDate.setText(date != null ? dateFormat.format(date) : "Tanggal tidak tersedia");

                        // Set status
                        textViewStatus.setText("Status: " + (status != null ? status.toUpperCase() : "Tidak diketahui"));
                        textViewStatus.setTextColor(getStatusColor(status));

                        // Tampilkan alasan penolakan jika status ditolak
                        if ("rejected".equals(status)) {
                            textViewRejectReason.setVisibility(View.VISIBLE);
                            textViewRejectReason.setText("Alasan Penolakan: " +
                                    (rejectReason != null ? rejectReason : "Tidak ada alasan"));
                        } else {
                            textViewRejectReason.setVisibility(View.GONE);
                        }

                        // Muat gambar jika tersedia
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(imageUrl)
                                    .into(imageViewReport);
                            imageViewReport.setVisibility(View.VISIBLE);
                        } else {
                            imageViewReport.setVisibility(View.GONE);
                        }
                    } else {
                        Toast.makeText(this, "Laporan tidak ditemukan", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal memuat detail laporan: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    // Metode untuk mendapatkan warna status
    private int getStatusColor(String status) {
        if (status == null) return Color.GRAY;

        switch (status) {
            case "approved":
                return ContextCompat.getColor(this, R.color.green);
            case "rejected":
                return ContextCompat.getColor(this, R.color.red);
            default:
                return Color.GRAY;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PAYMENT && resultCode == RESULT_OK) {
            // Refresh detail laporan setelah pembayaran
            loadProcessedReportDetails();
        }
    }
}
