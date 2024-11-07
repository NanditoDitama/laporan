package com.example.laporan2;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SearchActivity extends AppCompatActivity {
    private static final String TAG = "SearchActivity";

    // UI Components
    private Button buttonSelectDate;
    private Button buttonReset;
    private EditText editTextSearch;
    private ListView listViewResults;
    private ProgressBar progressBar;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Data
    private ReportAdapter adapter;
    private List<Report> reportsList;
    private Calendar selectedDate;
    private boolean isDateSelected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        initializeViews();
        setupFirebase();
        setupListeners();
        loadAllReports();
    }

    private void initializeViews() {
        buttonSelectDate = findViewById(R.id.buttonSelectDate);
        buttonReset = findViewById(R.id.buttonReset);
        editTextSearch = findViewById(R.id.editTextSearch);
        listViewResults = findViewById(R.id.listViewSearchResults);
        progressBar = findViewById(R.id.progressBar);

        selectedDate = Calendar.getInstance();
        reportsList = new ArrayList<>();
        adapter = new ReportAdapter(this, reportsList);
        listViewResults.setAdapter(adapter);

        // Set adapter click listeners
        adapter.setOnItemClickListener(new ReportAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Report report) {
                showReportDetail(report);
            }

            @Override
            public void onDeleteClick(Report report) {
                showDeleteConfirmation(report);
            }

            @Override
            public void onEditClick(Report report) {
                editReport(report);
            }
        });
    }

    private void setupFirebase() {
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
    }

    private void setupListeners() {
        buttonSelectDate.setOnClickListener(v -> showDatePicker());
        buttonReset.setOnClickListener(v -> {
            resetDateFilter();
            loadAllReports();
        });

        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterReports(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showReportDetail(Report report) {
        Intent intent = new Intent(this, ReportDetailActivity.class);
        intent.putExtra("reportId", report.getId());
        intent.putExtra("title", report.getTitle());
        intent.putExtra("description", report.getDescription());
        intent.putExtra("date", report.getDate().getTime());
        intent.putExtra("imageUrl", report.getImageUrl());
        startActivity(intent);
    }

    private void editReport(Report report) {
        Intent intent = new Intent(this, EditReportActivity.class);
        intent.putExtra("reportId", report.getId());
        intent.putExtra("title", report.getTitle());
        intent.putExtra("description", report.getDescription());
        intent.putExtra("date", report.getDate().getTime());
        intent.putExtra("imageUrl", report.getImageUrl());
        startActivity(intent);
    }

    private void showDeleteConfirmation(Report report) {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Laporan")
                .setMessage("Apakah Anda yakin ingin menghapus laporan ini?")
                .setPositiveButton("Ya", (dialog, which) -> deleteReport(report))
                .setNegativeButton("Tidak", null)
                .show();
    }

    private void deleteReport(Report report) {
        showLoading();
        db.collection("reports")
                .document(report.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    hideLoading();
                    Toast.makeText(this, "Laporan berhasil dihapus", Toast.LENGTH_SHORT).show();
                    if (isDateSelected) {
                        filterByDate();
                    } else {
                        loadAllReports();
                    }
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    Toast.makeText(this, "Gagal menghapus laporan: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    if (isValidDate(selectedDate.getTime())) {
                        isDateSelected = true;
                        updateDateButtonText();
                        filterByDate();
                    } else {
                        Toast.makeText(this, "Tidak dapat memilih tanggal masa depan",
                                Toast.LENGTH_SHORT).show();
                    }
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void filterByDate() {
        showLoading();
        String userId = mAuth.getCurrentUser().getUid();

        Calendar startOfDay = (Calendar) selectedDate.clone();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);

        Calendar endOfDay = (Calendar) selectedDate.clone();
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);

        db.collection("reports")
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("date", startOfDay.getTime())
                .whereLessThanOrEqualTo("date", endOfDay.getTime())
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    hideLoading();
                    reportsList.clear();
                    reportsList.addAll(queryDocumentSnapshots.toObjects(Report.class));
                    adapter.notifyDataSetChanged();

                    if (reportsList.isEmpty()) {
                        Toast.makeText(this, "Tidak ada data pada tanggal yang dipilih",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    Log.e(TAG, "Error filtering by date: ", e);
                    Toast.makeText(this, "Error mengambil data: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void loadAllReports() {
        showLoading();
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("reports")
                .whereEqualTo("userId", userId)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    hideLoading();
                    reportsList.clear();
                    reportsList.addAll(queryDocumentSnapshots.toObjects(Report.class));
                    adapter.notifyDataSetChanged();

                    if (reportsList.isEmpty()) {
                        Toast.makeText(this, "Tidak ada laporan yang tersedia",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    Log.e(TAG, "Error loading reports: ", e);
                    Toast.makeText(this, "Error loading reports: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void filterReports(String searchText) {
        if (searchText.isEmpty() && !isDateSelected) {
            loadAllReports();
            return;
        }

        List<Report> filteredList = new ArrayList<>();
        for (Report report : reportsList) {
            if (report.getTitle().toLowerCase().contains(searchText.toLowerCase())) {
                filteredList.add(report);
            }
        }

        reportsList.clear();
        reportsList.addAll(filteredList);
        adapter.notifyDataSetChanged();

        if (filteredList.isEmpty()) {
            Toast.makeText(this, "Tidak ada hasil yang ditemukan",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isValidDate(Date date) {
        Calendar now = Calendar.getInstance();
        Calendar selected = Calendar.getInstance();
        selected.setTime(date);

        // Reset time component for accurate date comparison
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);

        selected.set(Calendar.HOUR_OF_DAY, 0);
        selected.set(Calendar.MINUTE, 0);
        selected.set(Calendar.SECOND, 0);
        selected.set(Calendar.MILLISECOND, 0);

        return !selected.after(now);
    }

    private void updateDateButtonText() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String dateString = sdf.format(selectedDate.getTime());
        buttonSelectDate.setText(dateString);
    }

    private void resetDateFilter() {
        isDateSelected = false;
        buttonSelectDate.setText("Pilih Tanggal");
        selectedDate = Calendar.getInstance();
        editTextSearch.setText("");
    }

    private void showLoading() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoading() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning to this activity
        if (isDateSelected) {
            filterByDate();
        } else {
            loadAllReports();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up resources if needed
        reportsList.clear();
    }
}