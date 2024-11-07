package com.example.laporan2;

import java.io.Serializable;
import java.util.Date;

public class History implements Serializable {
    private String id;
    private String reportId;
    private String userId;
    private String type; // "sent" atau "received"
    private Date date;

    public History() {}

    public History(String id, String reportId, String userId, String type, Date date) {
        this.id = id;
        this.reportId = reportId;
        this.userId = userId;
        this.type = type;
        this.date = date;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

}