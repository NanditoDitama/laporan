package com.example.laporan2;

import android.annotation.SuppressLint;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import pl.droidsonroids.gif.GifImageView;

public class PaymentProofDetailActivity extends AppCompatActivity {
    private TextView textViewPaidDate;
    private FirebaseFirestore db;


    private View enlargedImageContainer;
    private ImageView imageViewPaymentProof, imageViewEnlarged, openEnlargedImage, closeEnlargedImage;
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
        setContentView(R.layout.activity_payment_proof_detail);


        enlargedImageContainer = findViewById(R.id.enlargedImageContainer);
        imageViewEnlarged = findViewById(R.id.imageViewEnlarged);
        openEnlargedImage = findViewById(R.id.openEnlargedImage);
        closeEnlargedImage = findViewById(R.id.closeEnlargedImage);

        // Panggil metode setup
        setupImageClickListeners();
        setupZoomFeature();


        // Inisialisasi view
        TextView textViewPaidStatus = findViewById(R.id.textViewPaidStatus);
        textViewPaidDate = findViewById(R.id.textViewPaidDate);
        TextView textViewImageInfo = findViewById(R.id.textViewImageInfo);
        imageViewPaymentProof = findViewById(R.id.imageViewPaymentProof);
        GifImageView gifImageView = findViewById(R.id.gifImageView);

        // Inisialisasi Firestore
        db = FirebaseFirestore.getInstance();

        // Ambil reportId dari intent
        String reportId = getIntent().getStringExtra("reportId");

        if (reportId != null) {
            loadPaymentProofDetails(reportId);
        }
    }

    private void loadPaymentProofDetails(String reportId) {
        db.collection("processedReports")
                .document(reportId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Ambil data pembayaran
                        Boolean isPaid = documentSnapshot.getBoolean("isPaid");
                        Date paidDate = documentSnapshot.getDate("paidDate");
                        String paymentProofUrl = documentSnapshot.getString("paymentProofUrl");

                        // Format tanggal
                        if (paidDate != null) {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy, HH:mm", new Locale("id", "ID"));
                            textViewPaidDate.setText("Dibayar pada: " + dateFormat.format(paidDate));
                        }

                        if (paymentProofUrl != null && !paymentProofUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(paymentProofUrl)
                                    .into(imageViewPaymentProof);
                            imageViewPaymentProof.setTag(paymentProofUrl); // Simpan URL gambar di tag
                            imageViewPaymentProof.setVisibility(View.VISIBLE);
                            openEnlargedImage.setVisibility(View.VISIBLE);
                        } else {
                            imageViewPaymentProof.setVisibility(View.GONE);
                            openEnlargedImage.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal memuat detail pembayaran", Toast.LENGTH_SHORT).show();
                });

        db.collection("reports")
                .document(reportId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Ambil data pembayaran
                        Boolean isPaid = documentSnapshot.getBoolean("isPaid");
                        Date paidDate = documentSnapshot.getDate("paidDate");
                        String paymentProofUrl = documentSnapshot.getString("paymentProofUrl");

                        // Format tanggal
                        if (paidDate != null) {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy, HH:mm", new Locale("id", "ID"));
                            textViewPaidDate.setText("Dibayar pada: " + dateFormat.format(paidDate));
                        }

                        // Muat gambar bukti pembayaran
                        if (paymentProofUrl != null && !paymentProofUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(paymentProofUrl)
                                    .into(imageViewPaymentProof);
                            imageViewPaymentProof.setTag(paymentProofUrl); // Simpan URL gambar di tag
                            imageViewPaymentProof.setVisibility(View.VISIBLE);
                            openEnlargedImage.setVisibility(View.VISIBLE);
                        } else {
                            imageViewPaymentProof.setVisibility(View.GONE);
                            openEnlargedImage.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal memuat detail pembayaran", Toast.LENGTH_SHORT).show();
                });
    }
    private void setupImageClickListeners() {
        openEnlargedImage.setOnClickListener(v -> {
            if (imageViewPaymentProof.getDrawable() != null) {
                enlargedImageContainer.setVisibility(View.VISIBLE);

                // Pastikan ImageView menggunakan matrix scale type
                imageViewEnlarged.setScaleType(ImageView.ScaleType.MATRIX);

                Glide.with(this)
                        .load(imageViewPaymentProof.getTag()) // Gunakan tag untuk menyimpan URL gambar
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
