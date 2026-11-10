package com.traceback.traceback.exception;

import com.traceback.traceback.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns every error into a clear JSON response with the right HTTP status:
 * 400 bad input, 404 not found, 409 conflict, 500 unexpected error.
 * Internal details (stack traces, SQL) are never sent to the browser.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // @Valid failed: collect a message for each invalid field
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        String message = fieldErrors.isEmpty()
                ? "Invalid request"
                : String.join(". ", fieldErrors.values());

        return build(HttpStatus.BAD_REQUEST, message, request, fieldErrors);
    }

    // Body is not valid JSON, or a value has the wrong type (e.g. bad date)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Request body is missing or has an invalid format", request, null);
    }

    // e.g. /api/reports/abc instead of a number
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Invalid value for '" + ex.getName() + "'", request, null);
    }

    // Two admins changed the same record at the same moment
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class,
                       DataIntegrityViolationException.class})
    public ResponseEntity<ApiError> handleConflict(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "This record was changed by someone else. Please refresh and try again.",
                request, null);
    }

    // Our own errors (ResponseStatusException) and Spring's built-in ones
    // such as 404 for unknown files or 405 for a wrong HTTP method
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest request) {

        if (ex instanceof ResponseStatusException rse) {
            return build(rse.getStatusCode(), rse.getReason(), request, null);
        }

        if (ex instanceof ErrorResponse errorResponse) {
            return build(errorResponse.getStatusCode(),
                    errorResponse.getBody().getDetail(), request, null);
        }

        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on the server. Please try again.", request, null);
    }

    private ResponseEntity<ApiError> build(
            HttpStatusCode status, String message,
            HttpServletRequest request, Map<String, String> fieldErrors) {

        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reason = resolved != null ? resolved.getReasonPhrase() : "Error";

        ApiError body = new ApiError(
                LocalDateTime.now(),
                status.value(),
                reason,
                message != null ? message : reason,
                request.getRequestURI(),
                fieldErrors == null ? Map.of() : fieldErrors);

        return ResponseEntity.status(status).body(body);
    }
}
