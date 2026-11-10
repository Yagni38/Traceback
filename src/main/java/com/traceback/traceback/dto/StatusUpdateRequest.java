package com.traceback.traceback.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/** Body of PATCH .../status requests, e.g. {"status": "RETURNED"}. */
// Any extra JSON fields are ignored
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatusUpdateRequest(
        @NotBlank(message = "Status is required")
        String status
) {
}
