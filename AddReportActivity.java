package com.example.laporan2;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import android.Manifest;

public class AddReportActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final String TAG = "AddReportActivity";
    private EditText editTextTitle, editTextDescription, editTextAmount;
    private Button buttonSubmitReport, buttonSelectDate, buttonSelectImage, buttonCamera;
    private ImageView imageViewPreview;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private Uri selectedImageUri;
    private Calendar selectedDate;
    private static final int CAMERA_REQUEST = 2;
    private static final int PICK_IMAGE_REQUEST = 1;
    private ProgressBar progressBar;
    private FrameLayout enlargedImageContainer;
    private ImageView imageViewEnlarged;
    private ImageView closeEnlargedImage;
    private Matrix matrix = new Matrix();
    private Matrix initialMatrix;
    private float scale;
    private float initialScale;
    private ScaleGestureDetector scaleGestureDetector;
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private int mode = NONE;
    private Report currentReport;
    private ImageView openEnlargedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_report);

        initializeFirebase();
        initializeViews();
        setupImageClickListeners();
        setupZoomFeature();
        setupListeners();
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE);
    }

    private void initializeFirebase() {
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        openEnlargedImage = findViewById(R.id.openEnlargedImage);
        openEnlargedImage.setVisibility(View.GONE);

    }

    private void initializeViews() {
        editTextTitle = findViewById(R.id.editTextTitle);
        editTextDescription = findViewById(R.id.editTextDescription);
        editTextAmount = findViewById(R.id.editTextAmount);
        buttonSubmitReport = findViewById(R.id.buttonSubmit);
        buttonSelectDate = findViewById(R.id.buttonSelectDate);
        buttonSelectImage = findViewById(R.id.buttonGallery);
        buttonCamera = findViewById(R.id.buttonCamera);
        imageViewPreview = findViewById(R.id.imageViewPreview);
        selectedDate = Calendar.getInstance();
        progressBar = findViewById(R.id.progressBar);
        enlargedImageContainer = findViewById(R.id.enlargedImageContainer);
        imageViewEnlarged = findViewById(R.id.imageViewEnlarged);
        closeEnlargedImage = findViewById(R.id.closeEnlargedImage);
        openEnlargedImage = findViewById(R.id.openEnlargedImage);
        matrix = new Matrix();
        initialMatrix = new Matrix();
    }

    private void setupListeners() {
        buttonSelectDate.setOnClickListener(v -> showDatePicker());
        buttonSelectImage.setOnClickListener(v -> selectImage());
        buttonCamera.setOnClickListener(v -> openCamera());
        buttonSubmitReport.setOnClickListener(v -> addReportToFirebase());
    }

    private void openCamera() {
        if (checkCameraPermission()) {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = createImageFile();
                if (photoFile != null) {
                    selectedImageUri = FileProvider.getUriForFile(this,
                            "com.example.laporan2.fileprovider",
                            photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, selectedImageUri);
                    startActivityForResult(takePictureIntent, CAMERA_REQUEST);
                }
            }
        } else {
            // Tampilkan dialog penjelasan jika perlu
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                new AlertDialog.Builder(this)
                        .setTitle("Izin Kamera Diperlukan")
                        .setMessage("Aplikasi memerlukan izin untuk mengakses kamera. Izinkan?")
                        .setPositiveButton("Ya", (dialog, which) -> {
                            requestCameraPermission();
                        })
                        .setNegativeButton("Tidak", (dialog, which) -> {
                            dialog.dismiss();
                            Toast.makeText(this, "Izin kamera diperlukan untuk mengambil foto",
                                    Toast.LENGTH_SHORT).show();
                        })
                        .create()
                        .show();
            } else {
                requestCameraPermission();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, buka kamera
                openCamera();
            } else {
                Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private File createImageFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = null;
        try {
            image = File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (Exception e) {
            Log.e(TAG, "Error creating image file: " + e.getMessage());
            Toast.makeText(this, "Gagal membuat file gambar", Toast.LENGTH_SHORT).show();
        }
        return image;
    }

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedDate.set(year, month, dayOfMonth);
            buttonSelectDate.setText(String.format("%d/%d/%d", dayOfMonth, (month + 1), year));
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_IMAGE_REQUEST && data != null) {
                selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    imageViewPreview.setImageURI(selectedImageUri);
                    openEnlargedImage.setVisibility(View.VISIBLE); // Tampilkan icon zoom
                    setupImageClickListeners();
                    setupZoomFeature();
                }
            } else if (requestCode == CAMERA_REQUEST) {
                if (selectedImageUri != null) {
                    imageViewPreview.setImageURI(selectedImageUri);
                    openEnlargedImage.setVisibility(View.VISIBLE); // Tampilkan icon zoom
                    setupImageClickListeners();
                    setupZoomFeature();
                }
            }
        }
    }

    private void addReportToFirebase() {
        String title = editTextTitle.getText().toString().trim();
        String description = editTextDescription.getText().toString().trim();
        String amountStr = editTextAmount.getText().toString().trim();

        if (title.isEmpty() || description.isEmpty() || amountStr.isEmpty()) {
            Toast.makeText(this, "Silakan lengkapi semua field", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Jumlah tidak valid", Toast.LENGTH_SHORT).show();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        buttonSubmitReport.setEnabled(false);
        Timestamp date = new Timestamp(selectedDate.getTime());

        if (selectedImageUri != null) {
            uploadImage(title, description, amount, date);
        } else {
            saveReportToFirestore(title, description, amount, date, null);
        }
    }

    private void uploadImage(String title, String description, double amount, Timestamp date) {
        String fileName = "report_images/" + UUID.randomUUID().toString() + getFileExtension(selectedImageUri);
        StorageReference storageRef = storage.getReference().child(fileName);

        storageRef.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String imageUrl = uri.toString();
                        saveReportToFirestore(title, description, amount, date, imageUrl);
                    });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    buttonSubmitReport.setEnabled(true);
                    Toast.makeText(AddReportActivity.this, "Gagal mengupload gambar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String getFileExtension(Uri uri) {
        String extension = "";
        String mimeType = getContentResolver().getType(uri);
        if (mimeType != null) {
            if (mimeType.contains("image/jpeg")) {
                extension = ".jpg";
            } else if (mimeType.contains("image/png")) {
                extension = ".png";
            } else if (mimeType.contains("image/gif")) {
                extension = ".gif";
            }
        }
        return extension;
    }

    private void saveReportToFirestore(String title, String description, double amount, Timestamp date, String imageUrl) {
        String userId = mAuth.getCurrentUser().getUid();
        String reportId = UUID.randomUUID().toString();

        // Menambahkan null sebagai recipientId karena ini laporan baru
        Report report = new Report(reportId, title, description, amount, date.toDate(), imageUrl, userId, null);

        db.collection("reports")
                .document(reportId)
                .set(report)
                .addOnSuccessListener(aVoid -> {
                    // Tambahkan history
                    String historyId = UUID.randomUUID().toString();
                    History history = new History(
                            historyId,
                            reportId,
                            userId,
                            "sent",
                            new Date()
                    );

                    db.collection("history")
                            .document(historyId)
                            .set(history)
                            .addOnSuccessListener(aVoid2 -> {
                                progressBar.setVisibility(View.GONE);
                                buttonSubmitReport.setEnabled(true);
                                Toast.makeText(this, "Laporan berhasil disimpan", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    buttonSubmitReport.setEnabled(true);
                    Toast.makeText(this, "Gagal menyimpan laporan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }


    private void setupImageClickListeners() {
        if (selectedImageUri != null) {
            openEnlargedImage.setOnClickListener(v -> {
                enlargedImageContainer.setVisibility(View.VISIBLE);
                imageViewEnlarged.setScaleType(ImageView.ScaleType.MATRIX);

                Glide.with(this)
                        .load(selectedImageUri)
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