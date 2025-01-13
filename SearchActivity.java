package com.example.laporan2;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.text.NumberFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SearchActivity extends AppCompatActivity {
    private static final String TAG = "SearchActivity";
    private TextView textViewTotalReports;
    private TextView textViewTotalAmount;
    // UI Components
    private Button buttonStartDate, buttonEndDate;
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
    private Calendar startDateCalendar, endDateCalendar;
    private boolean isDateSelected = false;
    private List<Report> originalReportsList; // Menyimpan daftar laporan asli

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
        buttonStartDate = findViewById(R.id.buttonStartDate);
        buttonEndDate = findViewById(R.id.buttonEndDate);
        buttonReset = findViewById(R.id.buttonReset);
        editTextSearch = findViewById(R.id.editTextSearch);
        listViewResults = findViewById(R.id.listViewSearchResults);
        progressBar = findViewById(R.id.progressBar);

        textViewTotalReports = findViewById(R.id.textViewTotalReports);
        textViewTotalAmount = findViewById(R.id.textViewTotalAmount);

        startDateCalendar = Calendar.getInstance();
        endDateCalendar = Calendar.getInstance();
        reportsList = new ArrayList<>();
        adapter = new ReportAdapter(this, reportsList, true);
        listViewResults.setAdapter(adapter);

        Button buttonFilterByUser = findViewById(R.id.buttonFilterByUser);
        buttonFilterByUser.setOnClickListener(v -> showUserSelectionDialog());

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
        buttonStartDate.setOnClickListener(v -> showDatePicker(true));
        buttonEndDate.setOnClickListener(v -> showDatePicker(false));
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
                .setNegativeButton(" Tidak", null)
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
                        filterByDateRange(); // Ganti filterByDate() dengan filterByDateRange()
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

    private void showDatePicker(boolean isStartDate) {
        Calendar currentCalendar = isStartDate ? startDateCalendar : endDateCalendar;

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedCalendar = Calendar.getInstance();
                    selectedCalendar.set(Calendar.YEAR, year);
                    selectedCalendar.set(Calendar.MONTH, month);
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    if (isValidDate(selectedCalendar.getTime())) {
                        if (isStartDate) {
                            startDateCalendar = selectedCalendar;
                            updateDateButtonText(buttonStartDate, startDateCalendar);
                        } else {
                            endDateCalendar = selectedCalendar;
                            updateDateButtonText(buttonEndDate, endDateCalendar);
                        }

                        // Jika kedua tanggal sudah dipilih, lakukan filter
                        if (startDateCalendar != null && endDateCalendar != null) {
                            isDateSelected = true; // Tandai bahwa tanggal telah dipilih
                            filterByDateRange();
                        }
                    } else {
                        Toast.makeText(this, "Tidak dapat memilih tanggal masa depan",
                                Toast.LENGTH_SHORT).show();
                    }
                },
                currentCalendar.get(Calendar.YEAR),
                currentCalendar.get(Calendar.MONTH),
                currentCalendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }
    private void updateDateButtonText(Button button, Calendar calendar) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String dateString = sdf.format(calendar.getTime());
        button.setText(dateString);
    }

    private void filterByDateRange() {
        if (startDateCalendar == null || endDateCalendar == null) {
            return;
        }

        // Pastikan start date tidak lebih besar dari end date
        if (startDateCalendar.getTime().after(endDateCalendar.getTime())) {
            Toast.makeText(this, "Tanggal awal harus sebelum tanggal akhir",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading();
        String userId = mAuth.getCurrentUser ().getUid();

        // Set waktu untuk start date ke awal hari
        Calendar startOfDay = (Calendar) startDateCalendar.clone();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);

        // Set waktu untuk end date ke akhir hari
        Calendar endOfDay = (Calendar) endDateCalendar.clone();
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

                    // Tambahkan pemanggilan updateTotals()
                    updateTotals();

                    if (reportsList.isEmpty()) {
                        Toast.makeText(this, "Tidak ada data pada rentang tanggal yang dipilih",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    Log.e(TAG, "Error filtering by date range: ", e);
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

                    // Simpan seluruh data asli
                    originalReportsList = queryDocumentSnapshots.toObjects(Report.class);

                    // Clear dan isi ulang reportsList
                    reportsList.clear();
                    reportsList.addAll(originalReportsList);

                    adapter.notifyDataSetChanged();
                    updateTotals();

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

        // Tambahkan pemanggilan updateTotals()
        updateTotals();

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

    private void resetDateFilter() {
        isDateSelected = false;
        buttonStartDate.setText("Tanggal Mulai");
        buttonEndDate.setText("Tanggal Akhir");
        startDateCalendar = Calendar.getInstance();
        endDateCalendar = Calendar.getInstance();
        editTextSearch.setText("");
        loadAllReports();
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
            filterByDateRange(); // Ganti filterByDate() dengan filterByDateRange()
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



    private void updateTotals() {
        int totalReports = reportsList.size();
        double totalAmount = 0.0;

        for (Report report : reportsList) {
            totalAmount += report.getAmount();
        }

        // Format total amount dalam Rupiah
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

        textViewTotalReports.setText(String.format("Total Laporan: %d", totalReports));
        textViewTotalAmount.setText(String.format("Total Jumlah: %s", currencyFormat.format(totalAmount)));
    }

    private void showUserSelectionDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_user_filters, null);

        // Gunakan ID baru
        CheckBox checkBoxApproved = dialogView.findViewById(R.id.checkbox_approved);
        CheckBox checkBoxRejected = dialogView.findViewById(R.id.checkbox_rejected);
        CheckBox checkBoxPaid = dialogView.findViewById(R.id.checkbox_paid);
        CheckBox checkBoxUnpaid = dialogView.findViewById(R.id.checkbox_unpaid);

        // Buat dialog
        new AlertDialog.Builder(this)
                .setTitle("Filter Laporan")
                .setView(dialogView)
                .setPositiveButton("Terapkan", (dialog, which) -> {
                    // Terapkan filter
                    filterReportsWithMultipleConditions(
                            checkBoxApproved.isChecked(),
                            checkBoxRejected.isChecked(),
                            checkBoxPaid.isChecked(),
                            checkBoxUnpaid.isChecked()
                    );
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void filterReportsWithMultipleConditions(
            boolean isApproved,
            boolean isRejected,
            boolean isPaid,
            boolean isUnpaid
    ) {
        // Pastikan originalReportsList tidak null
        if (originalReportsList == null) {
            loadAllReports(); // Muat ulang jika belum ada
            return;
        }

        List<Report> filteredList = new ArrayList<>(originalReportsList);

        // Filter tanggal jika sudah dipilih
        if (isDateSelected) {
            filteredList = filterByDateRange(filteredList);
        }

        // Filter status
        if (isApproved || isRejected) {
            filteredList = filterByStatus(filteredList, isApproved, isRejected);
        }

        // Filter pembayaran
        if (isPaid || isUnpaid) {
            filteredList = filterByPaymentStatus(filteredList, isPaid, isUnpaid);
        }

        // Update list dan adapter
        reportsList.clear();
        reportsList.addAll(filteredList);
        adapter.notifyDataSetChanged();

        // Tambahkan pemanggilan updateTotals()
        updateTotals();

        if (filteredList.isEmpty()) {
            Toast.makeText(this, "Tidak ada hasil yang ditemukan",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private List<Report> filterByStatus(List<Report> reports, boolean isApproved, boolean isRejected) {
        List<Report> filteredList = new ArrayList<>();

        for (Report report : reports) {
            if (isApproved && "approved".equals(report.getStatus())) {
                filteredList.add(report);
            }
            if (isRejected && "rejected".equals(report.getStatus())) {
                filteredList.add(report);
            }

            // Jika kedua status tidak dipilih, kembalikan seluruh list
            if (!isApproved && !isRejected) {
                return reports;
            }
        }

        return filteredList;
    }
    private List<Report> filterByDateRange(List<Report> reports) {
        List<Report> filteredList = new ArrayList<>();

        // Set waktu untuk start date ke awal hari
        Calendar startOfDay = (Calendar) startDateCalendar.clone();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);
        startOfDay.set(Calendar.MILLISECOND, 0);

        // Set waktu untuk end date ke akhir hari
        Calendar endOfDay = (Calendar) endDateCalendar.clone();
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);
        endOfDay.set(Calendar.MILLISECOND, 999);

        Date startDate = startOfDay.getTime();
        Date endDate = endOfDay.getTime();

        for (Report report : reports) {
            Date reportDate = report.getDate();

            // Periksa apakah tanggal laporan berada di dalam rentang yang dipilih
            if (reportDate != null &&
                    !reportDate.before(startDate) &&
                    !reportDate.after(endDate)) {
                filteredList.add(report);
            }
        }

        return filteredList;
    }
    private List<Report> filterByPaymentStatus(List<Report> reports, boolean isPaid, boolean isUnpaid) {
        List<Report> filteredList = new ArrayList<>();

        for (Report report : reports) {
            Boolean isPaidValue = report.getIsPaid();

            if (isPaid && Boolean.TRUE.equals(isPaidValue)) {
                filteredList.add(report);
            }
            if (isUnpaid && (isPaidValue == null || Boolean.FALSE.equals(isPaidValue))) {
                filteredList.add(report);
            }

            // Jika kedua status pembayaran tidak dipilih, kembalikan seluruh list
            if (!isPaid && !isUnpaid) {
                return reports;
            }
        }

        return filteredList;
    }




}