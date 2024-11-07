package com.example.laporan2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.laporan2.Report;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private List<Report> reports;
    private OnReportClickListener clickListener;
    private OnDeleteClickListener deleteListener;
    private boolean isSentData;
    private SimpleDateFormat dateFormat;

    public interface OnReportClickListener {
        void onReportClick(Report report);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(Report report);
    }

    public HistoryAdapter(List<Report> reports, OnReportClickListener clickListener,
                          OnDeleteClickListener deleteListener, boolean isSentData) {
        this.reports = reports;
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
        this.isSentData = isSentData;
        this.dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Report report = reports.get(position);

        // Set status text
        if (isSentData) {
            holder.textViewHistoryStatus.setText("Data telah dikirim kepada " + report.getReceiverName());
        } else {
            holder.textViewHistoryStatus.setText("Anda menerima data dari " + report.getSenderName());
        }

        // Set title
        holder.textViewHistoryTitle.setText(report.getTitle());

        // Set date
        if (report.getTimestamp() != null) {
            String formattedDate = dateFormat.format(report.getTimestamp().toDate());
            holder.textViewHistoryDate.setText(formattedDate);
        } else {
            holder.textViewHistoryDate.setText("Tanggal tidak tersedia");
        }

        // Set visibility of unread indicator
        boolean isRead = isSentData ? report.isReadSender() : report.isReadRecipient();
        holder.unreadIndicator.setVisibility(isRead ? View.GONE : View.VISIBLE);

        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onReportClick(report);
                report.setReadSender(true);
                report.setReadRecipient(true);
                notifyItemChanged(position);
            }
        });

        holder.buttonHistoryDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDeleteClick(report);
            }
        });
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewHistoryStatus;
        TextView textViewHistoryTitle;
        TextView textViewHistoryDate;
        ImageButton buttonHistoryDelete;
        View unreadIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewHistoryStatus = itemView.findViewById(R.id.textViewHistoryStatus);
            textViewHistoryTitle = itemView.findViewById(R.id.textViewHistoryTitle);
            textViewHistoryDate = itemView.findViewById(R.id.textViewHistoryDate);
            buttonHistoryDelete = itemView.findViewById(R.id.buttonHistoryDelete);
            unreadIndicator = itemView.findViewById(R.id.unreadIndicator);
        }
    }
}