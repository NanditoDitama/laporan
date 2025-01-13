package com.example.laporan2;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class Report implements Parcelable {
    private String id;
    private String processedReportId;
    private String reportId;
    private transient DocumentSnapshot document;
    private Boolean isPaid;
    private String paymentProofUrl;
    private String title;
    private String description;
    private double amount;
    private Date date;
    private String imageUrl;
    private String userId;
    private String recipientId;
    private String senderName;
    private String receiverName;
    private Timestamp timestamp;
    private boolean isReadRecipient;
    private boolean isReadSender;
    private String status; // Tambahan: status laporan
    private String rejectReason; // Tambahan: alasan penolakan
    private String originalReportId; // ID laporan asli
    private String editStatus; // Status edit: "pending", "approved", "rejected"
    private Map<String, Object> originalData; // Menyimpan data asli
    private Map<String, Object> editedData; // Menyimpan data yang diedit

    public void setReportId(String reportId) {
        // Set ke berbagai field yang mungkin digunakan
        this.originalReportId = reportId;
        this.reportId = reportId;

        // Jika id masih null, set juga
        if (this.id == null) {
            this.id = reportId;
        }
    }
    // Konstruktor kosong untuk Firestore
    public Report() {
    }

    // Konstruktor lengkap
    public Report(String id, String title, String description, double amount, Date date, String imageUrl, String userId, String recipientId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.amount = amount;
        this.date = date;
        this.imageUrl = imageUrl;
        this.userId = userId;
        this.recipientId = recipientId;
        this.status = "pending";
    }

    // Konstruktor untuk Parcelable
    protected Report(Parcel in) {
        id = in.readString();
        title = in.readString();
        description = in.readString();
        amount = in.readDouble();
        long tmpDate = in.readLong();
        date = tmpDate != -1 ? new Date(tmpDate) : null;
        imageUrl = in.readString();
        userId = in.readString();
        recipientId = in.readString();
        senderName = in.readString();
        receiverName = in.readString();
        timestamp = in.readParcelable(Timestamp.class.getClassLoader());
        isReadRecipient = in.readByte() != 0;
        isReadSender = in.readByte() != 0;
        this.processedReportId = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(title);
        dest.writeString(description);
        dest.writeDouble(amount);
        dest.writeLong(date != null ? date.getTime() : -1L);
        dest.writeString(imageUrl);
        dest.writeString(userId);
        dest.writeString(recipientId);
        dest.writeString(senderName);
        dest.writeString(receiverName);
        dest.writeParcelable(timestamp, flags);
        dest.writeByte((byte) (isReadRecipient ? 1 : 0));
        dest.writeByte((byte) (isReadSender ? 1 : 0));
        dest.writeString(processedReportId);

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Report report = (Report) o;
        return Objects.equals(id, report.id);
    }



    @Override
    public int hashCode() {
        return Objects.hash(id);
    }


    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Report> CREATOR = new Creator<Report>() {
        @Override
        public Report createFromParcel(Parcel in) {
            return new Report(in);
        }

        @Override
        public Report[] newArray(int size) {
            return new Report[size];
        }
    };
    public String getReportId() {
        // Prioritaskan sesuai urutan
        if (this.originalReportId != null) return this.originalReportId;
        if (this.reportId != null) return this.reportId;
        return this.id;
    }
    // Getter dan Setter
    public String getId() {
        return id; // Metode untuk mendapatkan ID
    }

    public void setId(String id) {
        this.id = id; // Metode untuk mengatur ID
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getSenderName() {
        return senderName != null ? senderName : "";
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getReceiverName() {
        return receiverName != null ? receiverName : "";
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        return date != null ? sdf.format(date) : "";
    }


    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date date) {
        this.timestamp = date != null ? new Timestamp(date) : null;
    }

    // Method tambahan untuk mendapatkan Date dari Timestamp
    public Date getTimestampAsDate() {
        return timestamp != null ? timestamp.toDate() : null;
    }

    // Method tambahan untuk mendapatkan string format dari timestamp
    public String getTimestampString() {
        if (timestamp != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
            return sdf.format(timestamp.toDate());
        }
        return "Tanggal tidak tersedia";
    }

    public boolean isReadSender() {
        return isReadSender;
    }

    public void setReadSender(boolean read) {
        this.isReadSender = read;
    }

    public boolean isReadRecipient() {
        return isReadRecipient;
    }

    public void setReadRecipient(boolean read) {
        this.isReadRecipient = read;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }
    // Getter dan setter untuk field baru
    public String getOriginalReportId() {
        return originalReportId;
    }


    public Boolean getIsPaid() {
        return isPaid;
    }

    public Boolean isPaid() {
        return isPaid != null && isPaid;
    }

    public String getProcessedReportId() {
        return processedReportId;
    }

    // Setter untuk processedReportId (opsional, tapi disarankan)
    public void setProcessedReportId(String processedReportId) {
        this.processedReportId = processedReportId;
    }

    public String getPaymentProofUrl() {
        return paymentProofUrl;
    }

    public void setPaymentProofUrl(String paymentProofUrl) {
        this.paymentProofUrl = paymentProofUrl;
    }

    public void setOriginalReportId(String originalReportId) {
        this.originalReportId = originalReportId;
    }

    public String getEditStatus() {
        return editStatus;
    }

    public void setEditStatus(String editStatus) {
        this.editStatus = editStatus;
    }

    public Map<String, Object> getOriginalData() {
        return originalData;
    }

    public void setOriginalData(Map<String, Object> originalData) {
        this.originalData = originalData;
    }

    public Map<String, Object> getEditedData() {
        return editedData;
    }

    public void setEditedData(Map<String, Object> editedData) {
        this.editedData = editedData;
    }

    @Override
    public String toString() {
        return "Report{" +
                "id='" + id + '\'' +
                ", processedReportId='" + processedReportId + '\'' +
                ", reportId='" + reportId + '\'' +
                ", title='" + title + '\'' +
                ", amount=" + amount +
                '}';
    }
    public void setDocument(DocumentSnapshot document) {
        this.document = document;
    }
    public DocumentSnapshot getDocument() {
        return document;
    }
}