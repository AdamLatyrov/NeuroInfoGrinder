package com.larbcorp.neuroinfogrinder.shared.exception;

import com.larbcorp.neuroinfogrinder.shared.dto.ApiErrorResponse;
import com.larbcorp.neuroinfogrinder.telegram.tdlib.TdlibException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .toList();

        return ResponseEntity.badRequest().body(
                new ApiErrorResponse("validation_error", "Request validation failed", details, Instant.now())
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        List<String> details = exception.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();

        return ResponseEntity.badRequest().body(
                new ApiErrorResponse("constraint_violation", "Constraint violation", details, Instant.now())
        );
    }

    @ExceptionHandler(TdlibException.class)
    public ResponseEntity<ApiErrorResponse> handleTdlibException(TdlibException exception) {
        String message = exception.getMessage() == null ? "TDLib error" : exception.getMessage();
        HttpStatus status = mapTdlibStatus(message);
        log.error("TDLib error [{}]: {}", status.value(), message, exception);

        return ResponseEntity.status(status).body(
                new ApiErrorResponse("tdlib_error", message, List.of(), Instant.now())
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Request failed" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(
                new ApiErrorResponse("request_error", message, List.of(), Instant.now())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ApiErrorResponse(
                        "internal_error",
                        exception.getMessage() == null ? "Unexpected server error" : exception.getMessage(),
                        List.of(),
                        Instant.now()
                )
        );
    }

    private HttpStatus mapTdlibStatus(String message) {
        if (message == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (message.contains("not ready") || message.contains("not authorized")
                || message.contains("authorization state")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (message.contains("Timed out") || message.contains("timed out")) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        if (message.contains("disabled") || message.contains("not configured")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (message.contains("not initialized") || message.contains("Failed to initialize")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
