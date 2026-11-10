package com.traceback.traceback.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Every error from the API has this same shape, so the frontend
 * can always show error.message (and fieldErrors for form problems).
 */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}
