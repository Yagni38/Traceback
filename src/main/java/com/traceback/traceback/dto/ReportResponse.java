package com.traceback.traceback.dto;

import com.traceback.traceback.entity.ItemReport;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * What the API sends back for a report.
 * The public sees masked contact details (e.g. "r***@gmail.com");
 * only a logged-in admin sees the full name, email and phone.
 */
public record ReportResponse(
        Long id,
        String reportType,
        String title,
        String category,
        String color,
        String description,
        String location,
        LocalDate eventDate,
        String status,
        LocalDateTime createdAt,
        String reporterName,
        String reporterEmail,
        String reporterPhone,
        boolean contactMasked
) {

    public static ReportResponse from(ItemReport r, boolean includeContact) {
        return new ReportResponse(
                r.getId(),
                r.getReportType(),
                r.getTitle(),
                r.getCategory(),
                r.getColor(),
                r.getDescription(),
                r.getLocation(),
                r.getEventDate(),
                r.getStatus(),
                r.getCreatedAt(),
                includeContact ? r.getReporterName() : maskName(r.getReporterName()),
                includeContact ? r.getReporterEmail() : maskEmail(r.getReporterEmail()),
                includeContact ? r.getReporterPhone() : maskPhone(r.getReporterPhone()),
                !includeContact
        );
    }

    /** "Ravi Kumar" -> "Ravi K." */
    static String maskName(String name) {
        if (name == null || name.isBlank()) return null;
        String[] parts = name.trim().split("\\s+");
        return parts.length == 1
                ? parts[0]
                : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    /** "ravi.kumar@gmail.com" -> "r***@gmail.com" */
    static String maskEmail(String email) {
        if (email == null || email.isBlank()) return null;
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** "9876543210" -> "******3210" */
    static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.trim();
        if (digits.length() <= 4) return "****";
        return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }
}
