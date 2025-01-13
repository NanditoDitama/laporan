package com.example.laporan2;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ItemDataEditAdapter extends RecyclerView.Adapter<ItemDataEditAdapter.ViewHolder> {
    private List<Report> reportList;
    private Context context;

    public ItemDataEditAdapter(Context context, List<Report> reportList) {
        this.context = context;
        this.reportList = reportList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_data_edit, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Report report = reportList.get(position);
        holder.textViewSender.setText("Pengirim: " + report.getSenderName());
        holder.textViewTitle.setText(report.getTitle());
        holder.textViewDate.setText(report.getDateString());

        // Set listener untuk item click
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, EditedReportDetailActivity.class);
            intent.putExtra("reportId", report.getId()); // Kirim ID laporan
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return reportList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewSender, textViewTitle, textViewDate;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewSender = itemView.findViewById(R.id.textViewSender);
            textViewTitle = itemView.findViewById(R.id.textViewTitle);
            textViewDate = itemView.findViewById(R.id.textViewDate);
        }
    }
}