package com.traceback.traceback.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "item_reports")
public class ItemReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reportType;
    private String title;
    private String category;
    private String color;
    private String reporterName;
    private String reporterEmail;
    private String reporterPhone;

    @Column(length = 1000)
    private String description;

    private String location;
    private LocalDate eventDate;
    private String status = "ACTIVE";
    private LocalDateTime createdAt;

    // Optimistic locking: stops two admins from overwriting each other's changes
    @Version
    private Long version;

    @PrePersist
    public void beforeSave() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getReporterName() {
    return reporterName;
}

public void setReporterName(String reporterName) {
    this.reporterName = reporterName;
}

public String getReporterEmail() {
    return reporterEmail;
}

public void setReporterEmail(String reporterEmail) {
    this.reporterEmail = reporterEmail;
}

public String getReporterPhone() {
    return reporterPhone;
}

public void setReporterPhone(String reporterPhone) {
    this.reporterPhone = reporterPhone;
}
}
