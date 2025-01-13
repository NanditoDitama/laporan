package com.example.laporan2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import android.text.TextUtils;
import com.google.android.gms.tasks.Tasks;

import java.util.UUID;
import java.util.concurrent.Executors;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.android.gms.tasks.Task;

import org.apache.commons.logging.LogFactory;

import pl.droidsonroids.gif.GifImageView;

// Tambahkan variabel untuk Firebase Storage




public class TransaksiActivity extends AppCompatActivity {
    private static final org.apache.commons.logging.Log log = LogFactory.getLog(TransaksiActivity.class);
    private TextView textViewTotalReports, textViewTotalAmount, textViewDetailTransaksi;
    private List<Report> filteredReports;
    private FirebaseStorage storage;
    private StorageReference storageReference;
    private String reportId;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_PICK_IMAGE = 2;
    private static final int PERMISSION_REQUEST_CAMERA = 3;
    private ImageView imageViewTransaksi;
    private Button buttonTakePhoto, buttonPickFromGallery, buttonMarkAsPaid;
    private Uri photoUri;
    private FrameLayout enlargedImageContainer;
    private ImageView imageViewEnlarged;
    private ImageView closeEnlargedImage;
    private Matrix matrix = new Matrix();
    private Matrix initialMatrix = new Matrix();
    private float scale;
    private float initialScale;
    private ScaleGestureDetector scaleGestureDetector;
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private int mode = NONE;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaksi);
        checkAndRequestPermissions();
        // Inisialisasi View PERTAMA KALI
        textViewTotalReports = findViewById(R.id.textViewTotalReports);
        textViewTotalAmount = findViewById(R.id.textViewTotalAmount);
        textViewDetailTransaksi = findViewById(R.id.textViewDetailTransaksi);
        imageViewTransaksi = findViewById(R.id.imageViewTransaksi);
        buttonTakePhoto = findViewById(R.id.buttonTakePhoto);
        buttonPickFromGallery = findViewById(R.id.buttonPickFromGallery);
        buttonMarkAsPaid = findViewById(R.id.buttonMarkAsPaid);
        // Ambil data dari Intent
        filteredReports = getIntent().getParcelableArrayListExtra("filteredReports");



        enlargedImageContainer = findViewById(R.id.enlargedImageContainer);
        imageViewEnlarged = findViewById(R.id.imageViewEnlarged);
        closeEnlargedImage = findViewById(R.id.closeEnlargedImage);





        storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference();

        // Hitung total
        if (filteredReports != null && !filteredReports.isEmpty()) {
            calculateAndDisplayTotals();
        } else {
            // Tampilkan pesan default jika tidak ada laporan
            textViewTotalReports.setText("Total Laporan: 0");
            textViewTotalAmount.setText("Total Jumlah: Rp 0");
            textViewDetailTransaksi.setText("Tidak ada transaksi");
        }

        // Listener untuk tombol
        buttonTakePhoto.setOnClickListener(v -> {
            // Periksa izin kamera
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                // Minta izin jika belum diberikan
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        PERMISSION_REQUEST_CAMERA);
            } else {
                // Jalankan intent untuk mengambil foto
                dispatchTakePictureIntent();
            }
        });

        buttonPickFromGallery.setOnClickListener(v -> {
            // Intent untuk memilih gambar dari galeri
            Intent pickPhoto = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(pickPhoto, REQUEST_PICK_IMAGE);
        });

        buttonMarkAsPaid.setOnClickListener(v -> {
            markReportsAsPaid();
        });

    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_PICK_IMAGE);
        }
    }


    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Pastikan ada aktivitas kamera yang bisa menangani intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Buat file untuk menyimpan foto
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error saat membuat file
                return;
            }

            // Lanjutkan jika file berhasil dibuat
            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(this,
                        "com.example.laporan2.fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    // Method untuk membuat file gambar
    private File createImageFile() throws IOException {
        // Buat nama file unik
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        // Direktori penyimpanan
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        // Buat file gambar
        return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
    }

    private void calculateAndDisplayTotals() {
        int totalReports = filteredReports.size();
        double totalAmount = 0.0;

        // String untuk menyimpan detail transaksi
        StringBuilder detailTransaksi = new StringBuilder();

        for (Report report : filteredReports) {
            totalAmount += report.getAmount();

            // Tambahkan detail setiap laporan
            // Gunakan format yang benar
            detailTransaksi.append(String.format(Locale.getDefault(),
                    "%s - Rp %,.2f\n",
                    report.getTitle(),
                    report.getAmount())
            );
        }

        // Format mata uang
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

        // Set tampilan
        textViewTotalReports.setText(getString(R.string.total_reports, totalReports));
        textViewTotalAmount.setText(getString(R.string.total_amount, currencyFormat.format(totalAmount)));
        textViewDetailTransaksi.setText(detailTransaksi.toString());
    }


    private void markReportsAsPaid() {
        try {
            // Periksa apakah filteredReports null atau kosong
            if (filteredReports == null || filteredReports.isEmpty()) {
                showNoReportsDialog();
                return;
            }

            // Periksa apakah photoUri null
            if (photoUri == null) {
                Toast.makeText(this, "Silakan pilih atau ambil foto terlebih dahulu", Toast.LENGTH_SHORT).show();
                return;
            }

            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Memproses pembayaran...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            StorageReference imageRef = storageReference.child("payment_proofs/" + timestamp + "_" + UUID.randomUUID().toString() + ".jpg");

            imageRef.putFile(photoUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            String imageUrl = uri.toString();
                            FirebaseFirestore db = FirebaseFirestore.getInstance();
                            Date currentDate = new Date();

                            // Batch untuk operasi multi-dokumen
                            WriteBatch batch = db.batch();

                            // List untuk menyimpan task pencarian dokumen
                            List<Task<QuerySnapshot>> searchTasks = new ArrayList<>();

                            for (Report report : filteredReports) {
                                String processedReportId = report.getProcessedReportId();

                                if (TextUtils.isEmpty(processedReportId)) {
                                    Log.e("MarkReportsPaid", "ProcessedReportId kosong - Lewati laporan");
                                    continue;
                                }

                                // Query untuk mencari dokumen reports berdasarkan processedReportId
                                Task<QuerySnapshot> searchTask = db.collection("reports")
                                        .whereEqualTo("processedReportId", processedReportId)
                                        .get();

                                searchTasks.add(searchTask);
                            }

                            // Tunggu semua pencarian selesai
                            Tasks.whenAllComplete(searchTasks)
                                    .addOnSuccessListener(searchResults -> {
                                        boolean hasValidReports = false;

                                        for (Task<QuerySnapshot> task : searchTasks) {
                                            if (task.isSuccessful()) {
                                                QuerySnapshot querySnapshot = task.getResult();
                                                if (!querySnapshot.isEmpty()) {
                                                    hasValidReports = true;
                                                    DocumentSnapshot reportDocument = querySnapshot.getDocuments().get(0);
                                                    String reportDocumentId = reportDocument.getId();

                                                    // Persiapan data update
                                                    Map<String, Object> processedReportUpdates = new HashMap<>();
                                                    processedReportUpdates.put("isPaid", true);
                                                    processedReportUpdates.put("paymentProofUrl", imageUrl);
                                                    processedReportUpdates.put("paidDate", currentDate);

                                                    Map<String, Object> reportUpdates = new HashMap<>();
                                                    reportUpdates.put("isPaid", true);
                                                    reportUpdates.put("paid", true);
                                                    reportUpdates.put("paymentProofUrl", imageUrl);
                                                    reportUpdates.put("paidDate", currentDate);

                                                    // Referensi dokumen
                                                    String processedReportRefId = reportDocument.getString("processedReportId");
                                                    if (processedReportRefId != null) {
                                                        DocumentReference processedReportRef =
                                                                db.collection("processedReports").document(processedReportRefId);
                                                        DocumentReference reportRef =
                                                                db.collection("reports").document(reportDocumentId);

                                                        // Tambahkan ke batch
                                                        batch.update(processedReportRef, processedReportUpdates);
                                                        batch.update(reportRef, reportUpdates);
                                                    }
                                                }
                                            }
                                        }

                                        // Jika tidak ada laporan valid, tampilkan dialog
                                        if (!hasValidReports) {
                                            progressDialog.dismiss();
                                            runOnUiThread(this::showNoReportsDialog);
                                            return;
                                        }

                                        // Commit batch
                                        batch.commit()
                                                .addOnSuccessListener(aVoid -> {
                                                    progressDialog.dismiss();
                                                    showCustomSuccessDialog();
                                                })
                                                .addOnFailureListener(e -> {
                                                    progressDialog.dismiss();
                                                    Log.e("MarkReportsPaid", "Gagal memperbarui laporan", e);
                                                    showErrorDialog(e.getMessage());
                                                });
                                    })
                                    .addOnFailureListener(e -> {
                                        progressDialog.dismiss();
                                        Log.e("MarkReportsPaid", "Gagal mencari dokumen", e);
                                        runOnUiThread(this::showNoReportsDialog);
                                    });
                        });
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        Log.e("MarkReportsPaid", "Gagal mengunggah bukti pembayaran", e);
                        Toast.makeText(this,
                                "Gagal mengunggah bukti pembayaran: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        } catch (Exception e) {
            // Tangani exception umum
            Log.e("MarkReportsPaid", "Error unexpected", e);
            showNoReportsDialog();
        }
    }
    private void showNoReportsDialog() {
        StringBuilder titleBuilder = new StringBuilder();

        // Jika filteredReports tidak null, kumpulkan title yang null
        if (filteredReports != null && !filteredReports.isEmpty()) {
            for (Report report : filteredReports) {
                // Cek jika title null
                if (report.getTitle() == null || report.getTitle().isEmpty()) {
                    titleBuilder.append("Laporan tanpa judul, ");
                } else {
                    titleBuilder.append(report.getTitle()).append(", ");
                }
            }
        }

        // Hapus koma terakhir jika ada
        String titleList = titleBuilder.length() > 0
                ? titleBuilder.substring(0, titleBuilder.length() - 2)
                : "Tidak ada judul laporan";

        String message = "Laporan dengan judul: " + titleList + "\n\n" +
                "Tidak dapat diproses. Kemungkinan:\n" +
                "• Data telah dihapus oleh User\n" +
                "• Laporan hilang dari database\n\n" +
                "Disarankan:\n" +
                "• Hapus Laporan dengan judul yang sama\n" +
                "• Coba kembali proses pembayaran";

        new AlertDialog.Builder(this)
                .setTitle("Gagal Menandai Laporan")
                .setMessage(message)
                .setNegativeButton("Tutup", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }


    private void showCustomSuccessDialog() {
        // Buat dialog custom
        Dialog dialog = new Dialog(this, R.style.CustomDialogStyle);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_payment_success);

        // Pastikan latar belakang dialog transparan
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Temukan views
        GifImageView gifImageView = dialog.findViewById(R.id.gifImageView);
        TextView titleText = dialog.findViewById(R.id.titleText);
        TextView messageText = dialog.findViewById(R.id.messageText);
        Button okButton = dialog.findViewById(R.id.okButton);

        // Atur tombol OK
        okButton.setOnClickListener(v -> {
            dialog.dismiss();
            setResult(RESULT_OK);
            Intent intent = new Intent(this, ProcessedDataFragment.class);
            startActivity(intent);
            finish();
        });

        // Tampilkan dialog
        dialog.show();
    }

    private void showErrorDialog(String errorMessage) {
        new AlertDialog.Builder(this)
                .setTitle("Gagal Memperbarui")
                .setMessage(errorMessage)
                .setPositiveButton("OK", null)
                .show();
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            try {
                Bitmap bitmap;
                if (requestCode == REQUEST_IMAGE_CAPTURE) {
                    // Gambar dari kamera
                    bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), photoUri);
                } else if (requestCode == REQUEST_PICK_IMAGE && data != null && data.getData() != null) {
                    // Gambar dari galeri
                    photoUri = data.getData();
                    bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), photoUri);
                } else {
                    return; // Tidak ada gambar yang diproses
                }

                // Atur gambar ke ImageView
                imageViewTransaksi.setImageBitmap(bitmap);

                // Siapkan fitur zoom dan click listener
                setupImageClickListeners();
                setupZoomFeature();

            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Gagal memuat gambar", Toast.LENGTH_SHORT).show();
            }
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Izin diberikan, buka kamera
                dispatchTakePictureIntent();
            } else {
                // Izin ditolak
                Toast.makeText(this, "Izin kamera diperlukan untuk mengambil foto", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void setupImageClickListeners() {
        if (photoUri != null) {
            imageViewTransaksi.setOnClickListener(v -> {
                enlargedImageContainer.setVisibility(View.VISIBLE);
                imageViewEnlarged.setScaleType(ImageView.ScaleType.MATRIX);

                Glide.with(this)
                        .load(photoUri)
                        .into(new CustomTarget<Drawable>() {
                            @Override
                            public void onResourceReady(@NonNull Drawable drawable,
                                                        @Nullable Transition<? super Drawable> transition) {
                                float imageWidth = drawable.getIntrinsicWidth();
                                float imageHeight = drawable.getIntrinsicHeight();
                                float containerWidth = enlargedImageContainer.getWidth();
                                float containerHeight = enlargedImageContainer.getHeight();

                                float scaleX = containerWidth / imageWidth;
                                float scaleY = containerHeight / imageHeight;
                                initialScale = Math.min(scaleX, scaleY);
                                scale = initialScale;

                                float scaledImageWidth = imageWidth * initialScale;
                                float scaledImageHeight = imageHeight * initialScale;
                                float translateX = (containerWidth - scaledImageWidth) / 2f;
                                float translateY = (containerHeight - scaledImageHeight) / 2f;

                                matrix.reset();
                                matrix.postScale(initialScale, initialScale);
                                matrix.postTranslate(translateX, translateY);

                                imageViewEnlarged.setImageDrawable(drawable);
                                imageViewEnlarged.setImageMatrix(matrix);

                                initialMatrix.set(matrix);
                            }

                            @Override
                            public void onLoadCleared(@Nullable Drawable placeholder) {}
                        });
            });

            closeEnlargedImage.setOnClickListener(v -> {
                enlargedImageContainer.setVisibility(View.GONE);
                resetZoom();
            });
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupZoomFeature() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                float newScale = scale * scaleFactor;

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

                            if (imageWidth > viewWidth) {
                                if (transX + deltaX > 0) deltaX = -transX;
                                else if (transX + deltaX < viewWidth - imageWidth)
                                    deltaX = viewWidth - imageWidth - transX;
                            } else {
                                deltaX = 0;
                            }

                            if (imageHeight > viewHeight) {
                                if (transY + deltaY > 0) deltaY = -transY;
                                else if (transY + deltaY < viewHeight - imageHeight)
                                    deltaY = viewHeight - imageHeight - transY;
                            } else {
                                deltaY = 0;
                            }

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
