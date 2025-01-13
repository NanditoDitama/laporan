package com.example.laporan2;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ReportAdapter extends BaseAdapter {
    private boolean isSearchActivity;
    private Context context;
    private List<Report> reportList;
    private OnItemClickListener listener;

    public ReportAdapter(Context context, List<Report> reportList, boolean isSearchActivity) {
        this.context = context;
        this.reportList = reportList;
        this.isSearchActivity = isSearchActivity; // Inisialisasi
    }


    @Override
    public int getCount() {
        return reportList.size();
    }

    @Override
    public Report getItem(int position) {
        return reportList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_report, parent, false);
            holder = new ViewHolder();
            holder.textViewStatus = convertView.findViewById(R.id.textViewStatus);
            holder.textViewTitle = convertView.findViewById(R.id.textViewTitle);
            holder.textViewDate = convertView.findViewById(R.id.textViewDate);
            holder.textViewAmount = convertView.findViewById(R.id.textViewAmount); // Tambahkan TextView Amount
            holder.buttonDelete = convertView.findViewById(R.id.buttonDelete);
            holder.buttonEdit = convertView.findViewById(R.id.buttonEdit);
            holder.textViewPaidStatus = convertView.findViewById(R.id.textViewPaidStatus);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Report report = reportList.get(position);

        if (Boolean.TRUE.equals(report.getIsPaid())) {
            holder.textViewPaidStatus.setVisibility(View.VISIBLE);
        } else {
            holder.textViewPaidStatus.setVisibility(View.GONE);
        }


        // Tambahkan logika untuk menampilkan amount
        if (isSearchActivity && report.getAmount() != 0) {
            holder.textViewAmount.setVisibility(View.VISIBLE);
            holder.textViewAmount.setText(formatRupiah(report.getAmount()));
        } else {
            holder.textViewAmount.setVisibility(View.GONE);
        }
        // Set data ke view
        if (report != null) {
            TextView statusIndicator = convertView.findViewById(R.id.textViewStatus);
            // Untuk status "pending"
            if ("pending".equals(report.getStatus())) {
                // Sembunyikan judul dan tanggal
                holder.textViewTitle.setVisibility(View.VISIBLE);
                holder.textViewDate.setVisibility(View.VISIBLE);
                holder.textViewTitle.setText(report.getTitle());
                // Tampilkan status dengan ukuran yang lebih besar
                holder.textViewStatus.setVisibility(View.VISIBLE);
                holder.textViewDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                // Atur ukuran teks status
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                holder.textViewDate.setText(sdf.format(report.getDate()));
                // Opsional: Bisa tambahkan warna atau gaya tambahan
                holder.textViewStatus.setTypeface(null, Typeface.BOLD);
            } else {
                // Untuk status selain "pending"
                holder.textViewTitle.setVisibility(View.VISIBLE);
                holder.textViewDate.setVisibility(View.VISIBLE);
                holder.textViewStatus.setVisibility(View.GONE);

                holder.textViewTitle.setText(report.getTitle());

                // Atur ukuran tanggal lebih kecil
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                holder.textViewDate.setText(sdf.format(report.getDate()));
                holder.textViewDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13); // Ukuran teks lebih kecil
            }

            // Listener untuk seluruh item
            convertView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(report);
                }
            });

            // Listener untuk tombol delete
            holder.buttonDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(report);
                }
            });

            // Listener untuk tombol edit
            holder.buttonEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditClick(report);
                }
            });



            // Tambahkan indikasi status pending
            if ("pending".equals(report.getStatus())) {
                // Set warna latar belakang khusus untuk status "pending"
                statusIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.pending_status_color));
                statusIndicator.setText("Menunggu Persetujuan");
                statusIndicator.setVisibility(View.VISIBLE);
                holder.buttonEdit.setVisibility(View.GONE); // Sembunyikan tombol edit
            } else if ("rejected".equals(report.getStatus())) {
                // Set warna latar belakang khusus untuk status "rejected"
                statusIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.rejected_status_color));
                statusIndicator.setText("Ditolak");
                statusIndicator.setVisibility(View.VISIBLE);
                holder.buttonEdit.setVisibility(View.VISIBLE); // Tampilkan tombol edit
            } else if ("approved".equals(report.getStatus())) {
                // Set warna latar belakang khusus untuk status "approved"
                statusIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.colorApproved));
                statusIndicator.setText("Disetujui");
                statusIndicator.setVisibility(View.VISIBLE);
                holder.buttonEdit.setVisibility(View.GONE); // Sembunyikan tombol edit
            } else {
                // Status lainnya, sembunyikan status indicator
                statusIndicator.setBackgroundColor(Color.TRANSPARENT);
                statusIndicator.setVisibility(View.GONE);
            }

            // Atur judul dan tanggal
            holder.textViewTitle.setVisibility(View.VISIBLE);
            holder.textViewTitle.setText(report.getTitle());
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            holder.textViewDate.setText(sdf.format(report.getDate()));
            holder.textViewDate.setVisibility(View.VISIBLE);

            // Listener untuk item dan tombol
            convertView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(report);
                }
            });

            holder.buttonDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(report);
                }
            });

            holder.buttonEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditClick(report);
                }
            });


            return convertView;
        }

        // Sembunyikan/tampilkan tombol edit berdasarkan status
        if (report.getStatus() != null) {
            switch (report.getStatus()) {
                case "pending":
                    // Sembunyikan tombol edit saat menunggu persetujuan
                    holder.buttonEdit.setVisibility(View.GONE);
                    break;
                case "rejected":
                    // Tampilkan tombol edit jika ditolak
                    holder.buttonEdit.setVisibility(View.VISIBLE);
                    break;
                case "approved":
                    // Sembunyikan tombol edit jika disetujui
                    holder.buttonEdit.setVisibility(View.GONE);
                    break;
                default:
                    holder.buttonEdit.setVisibility(View.VISIBLE);
            }
        }

        return convertView;
    }

    public void updateData(List<Report> newReportList) {
        this.reportList = newReportList;
        notifyDataSetChanged();
    }
    private int getColorFromTheme(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }


    private String formatRupiah(Double amount) {
        if (amount == null) return "Rp 0";
        return NumberFormat.getCurrencyInstance(new Locale("in", "ID")).format(amount);
    }

    static class ViewHolder {
        TextView textViewTitle, textViewDate, textViewStatus, textViewAmount, textViewPaidStatus;
        ImageButton buttonDelete, buttonEdit;
    }

    public interface OnItemClickListener {
        void onItemClick(Report report);
        void onDeleteClick(Report report);
        void onEditClick(Report report);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

}