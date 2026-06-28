package com.larbcorp.neuroinfogrinder2.api;

import com.larbcorp.neuroinfogrinder2.telegram.tdlib.TdlibException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class Stage1ExceptionHandler {
    @ExceptionHandler(TdlibException.class)
    public ResponseEntity<ApiError> tdlibException(TdlibException error) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiError("TDLIB_NOT_READY", safeMessage(error), OffsetDateTime.now().toString()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException error) {
        return ResponseEntity
                .badRequest()
                .body(new ApiError("BAD_REQUEST", safeMessage(error), OffsetDateTime.now().toString()));
    }

    private String safeMessage(Exception error) {
        if (error.getMessage() == null || error.getMessage().isBlank()) {
            return error.getClass().getSimpleName();
        }
        String message = error.getMessage();
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    public record ApiError(String code, String message, String timestamp) {
    }
}
