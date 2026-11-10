package com.traceback.traceback.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Data a user is allowed to send when reporting an item.
 * Fields like id, status and createdAt are NOT here, so a user
 * cannot set them (they are decided by the server).
 */
// Unknown JSON fields (e.g. "id" or "status") are ignored, never applied
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportRequest(

        @NotBlank(message = "Report type is required")
        @Pattern(regexp = "LOST|FOUND", flags = Pattern.Flag.CASE_INSENSITIVE,
                 message = "Report type must be LOST or FOUND")
        String reportType,

        @NotBlank(message = "Item name is required")
        @Size(max = 100, message = "Item name must be at most 100 characters")
        String title,

        @NotBlank(message = "Category is required")
        @Size(max = 50, message = "Category must be at most 50 characters")
        String category,

        @Size(max = 40, message = "Color must be at most 40 characters")
        String color,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @NotBlank(message = "Location is required")
        @Size(max = 150, message = "Location must be at most 150 characters")
        String location,

        @PastOrPresent(message = "Date cannot be in the future")
        LocalDate eventDate,

        @NotBlank(message = "Your name is required")
        @Size(max = 80, message = "Name must be at most 80 characters")
        String reporterName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 120, message = "Email must be at most 120 characters")
        String reporterEmail,

        @Pattern(regexp = "^$|^[0-9]{10}$", message = "Phone number must be 10 digits")
        String reporterPhone
) {
}
