package com.chipIn.ChipIn.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Stable error response shape returned by {@code GlobalExceptionHandler}.
 *
 * The same shape is used for every error so clients can rely on it. The
 * {@code message} is always safe to surface (never raw exception text from
 * unexpected failures); the {@code traceId} ties a client report back to the
 * server log line that has the full stack.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorBody {

    OffsetDateTime timestamp;
    int status;
    String error;
    String message;
    String path;
    String traceId;
    List<FieldError> fieldErrors;

    @Value
    @Builder
    public static class FieldError {
        String field;
        String message;
    }
}
