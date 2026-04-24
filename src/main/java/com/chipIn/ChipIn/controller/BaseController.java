package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.util.ErrorResponse;
import com.chipIn.ChipIn.util.ResponseWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@Slf4j
public class BaseController {

    @ExceptionHandler(Exception.class) // Catch all exceptions
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        HttpStatus status = determineStatus(ex);
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                ex.getMessage()
        );
        log.error("Exception Caught ", ex);
        return ResponseEntity.status(status).body(errorResponse);
    }

    private HttpStatus determineStatus(Exception ex) {
        String message = ex.getMessage();
        if (message != null) {
            String lower = message.toLowerCase();
            if (lower.contains("not found")) {
                return HttpStatus.NOT_FOUND;
            }
            if (lower.contains("already exists")) {
                return HttpStatus.CONFLICT;
            }
            if (lower.contains("unauthorized") || lower.contains("forbidden")) {
                return HttpStatus.FORBIDDEN;
            }
            if (lower.contains("bad request") || lower.contains("invalid")) {
                return HttpStatus.BAD_REQUEST;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
