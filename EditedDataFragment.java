package com.example.laporan2;

import android.app.AlertDialog;
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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EditedDataFragment extends Fragment implements RefreshableFragment {
    private RecyclerView recyclerView;
    private ItemDataEditAdapter adapter;
    private List<Report> editedReports;
    private FirebaseFirestore db;
    private ProgressBar progressBar;
    private TextView textViewNoData;
    private ListenerRegistration editedReportsListener;



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edited_data, container, false);
        recyclerView = view.findViewById(R.id.recyclerViewEditedData);
        progressBar = view.findViewById(R.id.progressBar);
        textViewNoData = view.findViewById(R.id.textViewNoData);

        editedReports = new ArrayList<>();
        db = FirebaseFirestore.getInstance();

        setupRecyclerView();
        startEditedReportsListener();

        return view;
    }
    @Override
    public void refreshData() {
        // Metode untuk memuat ulang data
        startEditedReportsListener();
    }
    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ItemDataEditAdapter(getContext(), editedReports);
        recyclerView.setAdapter(adapter);
    }

    private void startEditedReportsListener() {
        progressBar.setVisibility(View.VISIBLE);

        editedReportsListener = db.collection("editedReports")
                .whereEqualTo("editStatus", "pending")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e("EditedDataFragment", "Error listening to edited reports", e);
                        progressBar.setVisibility(View.GONE);
                        return;
                    }

                    editedReports.clear();
                    for (DocumentSnapshot document : snapshots.getDocuments()) {
                        Map<String, Object> editedData = (Map<String, Object>) document.get("editedData");

                        Report report = new Report();
                        report.setId(document.getId());
                        report.setTitle(editedData.get("title").toString());
                        report.setDate(((Timestamp) editedData.get("date")).toDate());

                        String userId = document.getString("userId");
                        getUserName(userId, report);

                        editedReports.add(report);
                    }

                    updateUIState();
                    progressBar.setVisibility(View.GONE);
                });
    }

    private void getUserName(String userId, Report report) {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        report.setSenderName(documentSnapshot.getString("name"));
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private void updateUIState() {
        if (editedReports.isEmpty()) {
            textViewNoData.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            textViewNoData.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (editedReportsListener != null) {
            editedReportsListener.remove();
        }
    }
}