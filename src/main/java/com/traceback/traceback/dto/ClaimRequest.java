package com.traceback.traceback.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Data a user sends when claiming a FOUND item. */
// Unknown JSON fields (e.g. "id" or "status") are ignored, never applied
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimRequest(

        @NotNull(message = "Report ID is required")
        Long reportId,

        @NotBlank(message = "Your name is required")
        @Size(max = 80, message = "Name must be at most 80 characters")
        String claimantName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 120, message = "Email must be at most 120 characters")
        String claimantEmail,

        @Pattern(regexp = "^$|^[0-9]{10}$", message = "Phone number must be 10 digits")
        String claimantPhone,

        @NotBlank(message = "Please describe why this item is yours")
        @Size(min = 10, max = 1000,
              message = "Proof of ownership must be between 10 and 1000 characters")
        String claimReason
) {
}
