package com.example.laporan2;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SearchReportActivity extends AppCompatActivity {
    private EditText editTextSearch;
    private RecyclerView recyclerViewSearchResults;
    private TextView textViewTotalReports, textViewTotalAmount;
    private Button buttonStartDate, buttonEndDate, buttonReset;
    private LinearLayout layoutTotalSummary;
    private ProcessedReportAdapter searchAdapter;
    private List<Report> allReports, filteredReports;
    private FirebaseFirestore db;
    private Calendar startDateCalendar, endDateCalendar;

    private boolean isDateSelected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_report);

        initializeViews();
        setupFirebase();
        setupListeners();
        fetchProcessedReports();
    }

    private void initializeViews() {
        editTextSearch = findViewById(R.id.editTextSearch);
        recyclerViewSearchResults = findViewById(R.id.recyclerViewSearchResults);
        textViewTotalReports = findViewById(R.id.textViewTotalReports);
        textViewTotalAmount = findViewById(R.id.textViewTotalAmount);
        buttonStartDate = findViewById(R.id.buttonStartDate);
        buttonEndDate = findViewById(R.id.buttonEndDate);
        buttonReset = findViewById(R.id.buttonReset);
        layoutTotalSummary = findViewById(R.id.layoutTotalSummary);

        recyclerViewSearchResults.setLayoutManager(new LinearLayoutManager(this));
        allReports = new ArrayList<>();
        filteredReports = new ArrayList<>();
        Button buttonFilterByUser = findViewById(R.id.buttonFilterByUser);
        buttonFilterByUser.setOnClickListener(v -> showUserSelectionDialog());

        layoutTotalSummary.setOnClickListener(v -> {
            Intent intent = new Intent(this, TransaksiActivity.class);
            intent.putParcelableArrayListExtra("filteredReports", new ArrayList<>(filteredReports));
            startActivity(intent);
        });

        searchAdapter = new ProcessedReportAdapter(this, filteredReports, report -> {
            // Detail report
            Intent intent = new Intent(this, ProcessedReportDetailActivity.class);
            intent.putExtra("reportId", report.getId());
            startActivity(intent);
        });

        recyclerViewSearchResults.setAdapter(searchAdapter);

        startDateCalendar = Calendar.getInstance();
        endDateCalendar = Calendar.getInstance();
    }

    private void setupFirebase() {
        db = FirebaseFirestore.getInstance();
    }

    private void setupListeners() {
        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterReports();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        buttonStartDate.setOnClickListener(v -> showDatePicker(true));
        buttonEndDate.setOnClickListener(v -> showDatePicker(false));
        buttonReset.setOnClickListener(v -> resetFilters());
    }

    private void fetchProcessedReports() {
        db.collection("processedReports")
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allReports.clear();
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        Report report = document.toObject(Report.class);

                        // Pastikan id di-set
                        report.setId(document.getId());

                        // Set processedReportId dan reportId
                        report.setProcessedReportId(document.getId());

                        // Ambil reportId dari dokumen
                        String reportId = document.getString("reportId");
                        report.setReportId(reportId);

                        // Optional: Tambahkan log untuk debug
                        Log.d("ReportFetch", "ProcessedReportId: " + report.getProcessedReportId() +
                                ", ReportId: " + report.getReportId());

                        allReports.add(report);
                    }
                    filterReports();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(SearchReportActivity.this,
                            "Error fetching reports: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar currentCalendar = isStartDate ? startDateCalendar : endDateCalendar;

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(year, month, dayOfMonth);

                    if (isStartDate) {
                        startDateCalendar = selectedDate;
                        updateDateButtonText(buttonStartDate, startDateCalendar);
                    } else {
                        endDateCalendar = selectedDate;
                        updateDateButtonText(buttonEndDate, endDateCalendar);
                    }

                    isDateSelected = true;
                    filterReports();
                },
                currentCalendar.get(Calendar.YEAR),
                currentCalendar.get(Calendar.MONTH),
                currentCalendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void updateDateButtonText(Button button, Calendar calendar) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        button.setText(sdf.format(calendar.getTime()));
    }

    private void filterReports() {
        filteredReports.clear();

        // Format tanggal untuk parsing
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        for (Report report : allReports) {
            boolean matchesDate = true;

            if (isDateSelected) {
                try {
                    // Parsing tanggal dari field date di Firestore
                    Date reportDate = report.getDate(); // Pastikan sudah dalam format Date

                    // Reset waktu untuk perbandingan yang akurat
                    Calendar reportCalendar = Calendar.getInstance();
                    reportCalendar.setTime(reportDate);

                    Calendar startCal = Calendar.getInstance();
                    startCal.setTime(startDateCalendar.getTime());
                    startCal.set(Calendar.HOUR_OF_DAY, 0);
                    startCal.set(Calendar.MINUTE, 0);
                    startCal.set(Calendar.SECOND, 0);
                    startCal.set(Calendar.MILLISECOND, 0);

                    Calendar endCal = Calendar.getInstance();
                    endCal.setTime(endDateCalendar.getTime());
                    endCal.set(Calendar.HOUR_OF_DAY, 23);
                    endCal.set(Calendar.MINUTE, 59);
                    endCal.set(Calendar.SECOND, 59);
                    endCal.set(Calendar.MILLISECOND, 999);

                    // Periksa apakah tanggal laporan berada di antara tanggal awal dan akhir
                    matchesDate = !reportCalendar.before(startCal) && !reportCalendar.after(endCal);
                } catch (Exception e) {
                    // Tangani kesalahan parsing tanggal
                    matchesDate = false;
                }
            }

            // Filter berdasarkan pencarian (opsional)
            boolean matchesSearch = report.getTitle().toLowerCase().contains(
                    editTextSearch.getText().toString().toLowerCase()
            );

            // Tambahkan laporan jika memenuhi kriteria
            if (matchesDate && matchesSearch) {
                filteredReports.add(report);
            }
        }

        updateTotals();
        searchAdapter.notifyDataSetChanged();
    }

    private void resetFilters() {
        isDateSelected = false;
        buttonStartDate.setText("Tanggal Mulai");
        buttonEndDate.setText("Tanggal Akhir");
        editTextSearch.setText("");
        filterReports();
    }

    private void updateTotals() {
        int totalReports = filteredReports.size();
        double totalAmount = 0.0;

        for (Report report : filteredReports) {
            totalAmount += report.getAmount();
        }

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
        textViewTotalReports.setText("Total Laporan: " + totalReports);
        textViewTotalAmount.setText("Total Jumlah: " + currencyFormat.format(totalAmount));
    }



    private void showUserSelectionDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_user_filter, null);

        // Checkbox untuk filter status
        CheckBox checkBoxApproved = dialogView.findViewById(R.id.checkBoxApproved);
        CheckBox checkBoxRejected = dialogView.findViewById(R.id.checkBoxRejected);

        // Checkbox untuk filter pembayaran
        CheckBox checkBoxPaid = dialogView.findViewById(R.id.checkBoxPaid);
        CheckBox checkBoxUnpaid = dialogView.findViewById(R.id.checkBoxUnpaid);

        // ListView untuk daftar user
        ListView listViewUsers = dialogView.findViewById(R.id.listViewUsers);

        // List untuk menyimpan user
        List<String> userNames = new ArrayList<>();
        List<String> userIds = new ArrayList<>();

        // Ambil daftar user
        db.collection("users")
                .whereEqualTo("isAdmin", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    userNames.clear();
                    userIds.clear();

                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        String name = document.getString("name");
                        if (name != null) {
                            userNames.add(name);
                            userIds.add(document.getId());
                        }
                    }

                    // Update adapter
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_list_item_multiple_choice,
                            userNames
                    );

                    listViewUsers.setAdapter(adapter);
                    listViewUsers.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

                    // Buat dialog
                    new AlertDialog.Builder(this)
                            .setTitle("Filter Laporan")
                            .setView(dialogView)
                            .setPositiveButton("Terapkan", (dialog, which) -> {
                                // Ambil user yang dipilih
                                SparseBooleanArray checkedItems = listViewUsers.getCheckedItemPositions();
                                List<String> selectedUserIds = new ArrayList<>();

                                for (int i = 0; i < userNames.size(); i++) {
                                    if (checkedItems.get(i)) {
                                        selectedUserIds.add(userIds.get(i));
                                    }
                                }

                                // Terapkan filter
                                filterReportsWithMultipleConditions(
                                        selectedUserIds,
                                        checkBoxApproved.isChecked(),
                                        checkBoxRejected.isChecked(),
                                        checkBoxPaid.isChecked(),
                                        checkBoxUnpaid.isChecked()
                                );
                            })
                            .setNegativeButton("Batal", null)
                            .show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Gagal mengambil daftar pengguna", Toast.LENGTH_SHORT).show();
                });
    }

    private void filterReportsWithMultipleConditions(
            List<String> selectedUserIds,
            boolean isApproved,
            boolean isRejected,
            boolean isPaid,
            boolean isUnpaid
    ) {
        filteredReports.clear();

        for (Report report : allReports) {
            // Filter tanggal
            boolean dateMatch = true;
            if (isDateSelected) {
                try {
                    Date reportDate = report.getDate();

                    Calendar reportCalendar = Calendar.getInstance();
                    reportCalendar.setTime(reportDate);

                    Calendar startCal = Calendar.getInstance();
                    startCal.setTime(startDateCalendar.getTime());
                    startCal.set(Calendar.HOUR_OF_DAY, 0);
                    startCal.set(Calendar.MINUTE, 0);
                    startCal.set(Calendar.SECOND, 0);
                    startCal.set(Calendar.MILLISECOND, 0);

                    Calendar endCal = Calendar.getInstance();
                    endCal.setTime(endDateCalendar.getTime());
                    endCal.set(Calendar.HOUR_OF_DAY, 23);
                    endCal.set(Calendar.MINUTE, 59);
                    endCal.set(Calendar.SECOND, 59);
                    endCal.set(Calendar.MILLISECOND, 999);

                    // Periksa apakah tanggal laporan berada di antara tanggal awal dan akhir
                    dateMatch = !reportCalendar.before(startCal) && !reportCalendar.after(endCal);
                } catch (Exception e) {
                    dateMatch = false;
                }
            }

            // Filter user
            boolean userMatch = selectedUserIds.isEmpty() ||
                    (report.getUserId() != null && selectedUserIds.contains(report.getUserId()));

            // Filter status
            boolean statusMatch = true;
            if (isApproved || isRejected) {
                statusMatch = false;
                if (isApproved && "approved".equals(report.getStatus())) {
                    statusMatch = true;
                }
                if (isRejected && "rejected".equals(report.getStatus())) {
                    statusMatch = true;
                }
            }

            // Filter pembayaran
            boolean paymentMatch = true;
            if (isPaid || isUnpaid) {
                paymentMatch = false;

                // Periksa keberadaan field dengan cara paling langsung
                Boolean isPaidValue = report.getIsPaid();

                if (isPaid && Boolean.TRUE.equals(isPaidValue)) {
                    paymentMatch = true;
                }

                if (isUnpaid && (isPaidValue == null || Boolean.FALSE.equals(isPaidValue))) {
                    paymentMatch = true;
                }
            }

            // Tambahkan laporan jika memenuhi semua kondisi
            if (dateMatch && userMatch && statusMatch && paymentMatch) {
                filteredReports.add(report);
            }
        }

        updateTotals();
        searchAdapter.notifyDataSetChanged();

        // Tampilkan pesan jika tidak ada hasil
        if (filteredReports.isEmpty()) {
            Toast.makeText(this, "Tidak ada hasil yang ditemukan", Toast.LENGTH_SHORT).show();
        }
    }
}
