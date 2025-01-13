package com.example.laporan2;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.view.View;
import android.graphics.Color;


import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import androidx.appcompat.app.AlertDialog;



public class ProcessedDataFragment extends Fragment implements RefreshableFragment {
    private RecyclerView recyclerViewProcessedData;
    private ProcessedReportAdapter adapter;
    private List<Report> processedReportsList;
    private FirebaseFirestore db;
    private ProgressBar progressBarProcessed;
    private TextView textViewEmptyProcessed;
    private ListenerRegistration processedReportsListener; // Tambahkan ini
    private FloatingActionButton fabSearch;





    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_processed_data, container, false);
        initializeFirebase();
        recyclerViewProcessedData = view.findViewById(R.id.recyclerViewProcessedData);
        progressBarProcessed = view.findViewById(R.id.progressBarProcessed);
        textViewEmptyProcessed = view.findViewById(R.id.textViewEmptyProcessed);

        db = FirebaseFirestore.getInstance();
        processedReportsList = new ArrayList<>();

        fabSearch = view.findViewById(R.id.fabSearch);
        fabSearch.setOnClickListener(v -> {
            // Buka halaman pencarian
            Intent intent = new Intent(getActivity(), SearchReportActivity.class);
            startActivity(intent);
        });
        setupRecyclerView();
        startProcessedReportsListener();
        setupSwipeToDelete();

        return view;
    }


    private void initializeFirebase() {
        try {
            // Cek apakah konteks valid
            if (requireContext() != null) {
                db = FirebaseFirestore.getInstance();
            } else {
                throw new IllegalStateException("Context is null");
            }
        } catch (Exception e) {
            Log.e("FirebaseInit", "Initialization error", e);
            showFirebaseInitializationErrorDialog(e);
        }
    }


    private void setupRecyclerView() {
        adapter = new ProcessedReportAdapter(
                getContext(),
                processedReportsList,
                report -> {
                    Intent intent = new Intent(getActivity(), ProcessedReportDetailActivity.class);
                    intent.putExtra("reportId", report.getId());
                    startActivity(intent);
                }
        );

        recyclerViewProcessedData.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewProcessedData.setAdapter(adapter);
    }


    @Override
    public void refreshData() {
        // Logika untuk memuat ulang data
        if (db != null) {
            loadProcessedReports();
        }
    }

    private void loadProcessedReports() {
        // Cek koneksi internet
        if (!isInternetAvailable()) {
            showNoInternetDialog();
            return;
        }

        // Validasi db tidak null
        if (db == null) {
            showFirebaseInitializationErrorDialog(new NullPointerException("Firestore instance is null"));
            return;
        }

        progressBarProcessed.setVisibility(View.VISIBLE);

        try {
            db.collection("processedReports")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        processedReportsList.clear();

                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            Report report = document.toObject(Report.class);
                            report.setId(document.getId());
                            processedReportsList.add(report);
                        }

                        updateUIState();
                        progressBarProcessed.setVisibility(View.GONE);
                    })
                    .addOnFailureListener(e -> {
                        progressBarProcessed.setVisibility(View.GONE);
                        showErrorDialog(e);
                    });
        } catch (Exception e) {
            progressBarProcessed.setVisibility(View.GONE);
            showErrorDialog(e);
        }
    }

    private void showFirebaseInitializationErrorDialog(Exception e) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Kesalahan Inisialisasi Firebase")
                .setMessage("Gagal menginisialisasi Firestore: " + e.getMessage())
                .setPositiveButton("Coba Lagi", (dialog, which) -> {
                    // Reset dan coba ulang
                    db = null;
                    initializeFirebase();
                    loadProcessedReports();
                })
                .setNegativeButton("Tutup", (dialog, which) -> {
                    // Tutup fragment atau activity jika diperlukan
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void showErrorDialog(Exception e) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Gagal Memuat Data")
                .setMessage("Terjadi kesalahan: " + e.getLocalizedMessage())
                .setPositiveButton("Coba Lagi", (dialog, which) -> loadProcessedReports())
                .setNegativeButton("Tutup", null)
                .setCancelable(false)
                .show();
    }

    private void showNoInternetDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Tidak Ada Koneksi Internet")
                .setMessage("Silakan periksa koneksi internet Anda")
                .setPositiveButton("Coba Lagi", (dialog, which) -> loadProcessedReports())
                .setNegativeButton("Tutup", null)
                .setCancelable(false)
                .show();
    }

    private boolean isInternetAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void startProcessedReportsListener() {
        progressBarProcessed.setVisibility(View.VISIBLE);

        processedReportsListener = db.collection("processedReports")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e("ProcessedDataFragment", "Error listening to processed reports", e);
                        progressBarProcessed.setVisibility(View.GONE);
                        return;
                    }

                    processedReportsList.clear();
                    for (QueryDocumentSnapshot document : snapshots) {
                        Report report = document.toObject(Report.class);
                        report.setId(document.getId());
                        processedReportsList.add(report);
                    }

                    updateUIState();
                    progressBarProcessed.setVisibility(View.GONE);
                });
    }



    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(
                0,
                ItemTouchHelper.RIGHT // Hanya izinkan geser ke kanan
        ) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Report reportToDelete = processedReportsList.get(position);

                // Periksa apakah laporan sudah dibayar
                if (reportToDelete.isPaid() != null && reportToDelete.isPaid()) {
                    // Tampilkan dialog bahwa laporan sudah dibayar
                    showPaidReportDialog(position);
                } else {
                    // Lanjutkan dengan dialog konfirmasi penghapusan
                    showDeleteConfirmationDialog(reportToDelete, position);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState,
                                    boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    View itemView = viewHolder.itemView;

                    // Batasi geser hanya ke kanan
                    float maxSwipeDistance = itemView.getWidth() * 0.2f; // Maksimal 30% lebar item
                    float clampedDX = Math.min(Math.max(dX, 0), maxSwipeDistance);

                    // Buat Paint untuk background
                    Paint backgroundPaint = new Paint();
                    backgroundPaint.setColor(ContextCompat.getColor(requireContext(), R.color.red));

                    // Gambar background delete
                    c.drawRect(
                            itemView.getLeft(),
                            itemView.getTop(),
                            clampedDX,
                            itemView.getBottom(),
                            backgroundPaint
                    );

                    // Gambar ikon delete
                    Drawable deleteIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete);
                    if (deleteIcon != null) {
                        // Sesuaikan margin untuk membuat ikon lebih dekat dengan tepi layar
                        int iconMargin = 62; // Ubah nilai ini untuk mengatur jarak dari tepi
                        int iconSize = Math.min(
                                deleteIcon.getIntrinsicWidth(),
                                deleteIcon.getIntrinsicHeight()
                        );

                        int iconTop = itemView.getTop() + (itemView.getHeight() - iconSize) / 2;
                        int iconBottom = iconTop + iconSize;

                        deleteIcon.setTint(Color.WHITE); // Pastikan ikon berwarna putih

                        deleteIcon.setBounds(
                                itemView.getLeft() + iconMargin,
                                iconTop,
                                itemView.getLeft() + iconMargin + iconSize,
                                iconBottom
                        );

                        // Hanya gambar ikon jika ada pergerakan
                        if (clampedDX > 0) {
                            deleteIcon.draw(c);
                        }
                    }

                    // Geser item sesuai dengan jarak yang dibatasi
                    viewHolder.itemView.setTranslationX(clampedDX);
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                // Atur ambang batas swipe
                return 0.5f;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                // Kembalikan item ke posisi semula
                super.clearView(recyclerView, viewHolder);

                // Reset translasi
                viewHolder.itemView.setTranslationX(0f);
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                // Pastikan swipe hanya aktif saat diperlukan
                return true;
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerViewProcessedData);
    }


    private void showPaidReportDialog(int position) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Laporan Tidak Dapat Dihapus")
                .setMessage("Laporan ini sudah dibayar dan tidak dapat dihapus.")
                .setPositiveButton("OK", (dialog, which) -> {
                    // Kembalikan item ke posisi semula
                    adapter.notifyItemChanged(position);
                })
                .setCancelable(false)
                .show();
    }



    private void showDeleteConfirmationDialog(Report report, int position) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Konfirmasi Hapus")
                .setMessage("Apakah Anda yakin ingin menghapus laporan ini?")
                .setPositiveButton("Ya", (dialog, which) -> {
                    // Proses penghapusan
                    deleteProcessedReport(report, position);
                })
                .setNegativeButton("Tidak", (dialog, which) -> {
                    // Kembalikan item ke posisi semula jika dibatalkan
                    adapter.notifyItemChanged(position);
                })
                .setOnCancelListener(dialog -> {
                    // Kembalikan item ke posisi semula jika dialog ditutup
                    adapter.notifyItemChanged(position);
                })
                .show();
    }

    private void deleteProcessedReport(Report report, int position) {
        // Hapus dari Firestore
        db.collection("processedReports")
                .document(report.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Hapus dari list lokal
                    processedReportsList.remove(position);
                    adapter.notifyItemRemoved(position);

                    // Tampilkan pesan berhasil
                    Toast.makeText(requireContext(), "Laporan berhasil dihapus", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    // Tampilkan pesan error
                    Toast.makeText(requireContext(), "Gagal menghapus laporan: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                    // Kembalikan item ke posisi semula
                    adapter.notifyItemChanged(position);
                });
    }

    private void updateUIState() {
        if (processedReportsList.isEmpty()) {
            textViewEmptyProcessed.setVisibility(View.VISIBLE);
            recyclerViewProcessedData.setVisibility(View.GONE);
        } else {
            textViewEmptyProcessed.setVisibility(View.GONE);
            recyclerViewProcessedData.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }




    @Override
    public void onStop() {
        super.onStop();
        if (processedReportsListener != null) {
            processedReportsListener.remove();
        }
    }
}