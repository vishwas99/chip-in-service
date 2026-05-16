package com.chipIn.ChipIn.config;

import com.chipIn.ChipIn.dto.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Single source of truth for HTTP error responses.
 *
 * Design rules:
 *   - Every response uses {@link ErrorBody} so clients can rely on the shape.
 *   - {@code message} is only the raw exception text for exceptions we
 *     explicitly trust to be safe (validation, ResponseStatusException with
 *     a server-authored reason, IllegalArgumentException). Generic 5xx never
 *     leaks {@code ex.getMessage()}.
 *   - Every response carries a {@code traceId} that ties back to the log line
 *     containing the full stack trace.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final int MAX_SAFE_MESSAGE_CHARS = 500;

    // ------------------------------------------------------------------ 400 --

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        String safe = truncate(ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, safe, req, null, ex, /*logStack=*/false);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorBody> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        List<ErrorBody.FieldError> fields = ex.getConstraintViolations().stream()
                .map(v -> ErrorBody.FieldError.builder()
                        .field(v.getPropertyPath().toString())
                        .message(truncate(v.getMessage()))
                        .build())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, fields, ex, /*logStack=*/false);
    }

    // ------------------------------------------------------------------ 401 --

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorBody> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Authentication required", req, null, ex, /*logStack=*/false);
    }

    // ------------------------------------------------------------------ 403 --

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorBody> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Access denied", req, null, ex, /*logStack=*/false);
    }

    // ------------------------------------------------------------------ 409 --

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorBody> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT,
                "Resource was modified by another request, please retry",
                req, null, ex, /*logStack=*/false);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorBody> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        // Don't expose constraint names or SQL — those are implementation detail.
        return build(HttpStatus.CONFLICT,
                "The request conflicts with the current state of the resource",
                req, null, ex, /*logStack=*/true);
    }

    // ------------------------------------------ ResponseStatusException --
    // Catches the explicit `throw new ResponseStatusException(...)` calls
    // sprinkled across services (e.g. AccessGuard, IdempotencyService).
    // The reason was authored by us, so it is safe to surface.

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorBody> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatusCode code = ex.getStatusCode();
        HttpStatus status = HttpStatus.resolve(code.value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = ex.getReason() != null ? truncate(ex.getReason()) : status.getReasonPhrase();
        return build(status, message, req, null, ex, /*logStack=*/status.is5xxServerError());
    }

    // ------------------------------------------------- generic catch-all --
    // Anything not handled above is bucketed as 500 with a GENERIC message —
    // we never echo back ex.getMessage() for unexpected failures.

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleUnexpected(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", req, null, ex, /*logStack=*/true);
    }

    // ------------------------------------------ overrides for @Valid bodies --

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<ErrorBody.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ErrorBody.FieldError.builder()
                        .field(fe.getField())
                        .message(truncate(fe.getDefaultMessage()))
                        .build())
                .toList();
        HttpServletRequest http = ((org.springframework.web.context.request.ServletWebRequest) request).getRequest();
        ResponseEntity<ErrorBody> body = build(HttpStatus.BAD_REQUEST, "Validation failed",
                http, fields, ex, /*logStack=*/false);
        return new ResponseEntity<>(body.getBody(), body.getHeaders(), body.getStatusCode());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        HttpServletRequest http = ((org.springframework.web.context.request.ServletWebRequest) request).getRequest();
        ResponseEntity<ErrorBody> body = build(HttpStatus.BAD_REQUEST,
                "Malformed JSON in request body", http, null, ex, /*logStack=*/false);
        return new ResponseEntity<>(body.getBody(), body.getHeaders(), body.getStatusCode());
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        HttpServletRequest http = ((org.springframework.web.context.request.ServletWebRequest) request).getRequest();
        ResponseEntity<ErrorBody> body = build(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method " + ex.getMethod() + " not allowed", http, null, ex, /*logStack=*/false);
        return new ResponseEntity<>(body.getBody(), body.getHeaders(), body.getStatusCode());
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        HttpServletRequest http = ((org.springframework.web.context.request.ServletWebRequest) request).getRequest();
        ResponseEntity<ErrorBody> body = build(HttpStatus.NOT_FOUND, "Resource not found",
                http, null, ex, /*logStack=*/false);
        return new ResponseEntity<>(body.getBody(), body.getHeaders(), body.getStatusCode());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorBody> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String requiredType = ex.getRequiredType() == null ? "expected type" : ex.getRequiredType().getSimpleName();
        String message = "Parameter '" + ex.getName() + "' must be a valid " + requiredType;
        return build(HttpStatus.BAD_REQUEST, message, req, null, ex, /*logStack=*/false);
    }

    // -------------------------------------------------------------- helpers --

    private ResponseEntity<ErrorBody> build(
            HttpStatus status,
            String message,
            HttpServletRequest req,
            List<ErrorBody.FieldError> fieldErrors,
            Throwable ex,
            boolean logStack) {

        String traceId = UUID.randomUUID().toString();
        String path = req == null ? null : req.getRequestURI();

        if (logStack) {
            log.error("traceId={} status={} path={} ex={}", traceId, status.value(), path,
                    ex == null ? "n/a" : ex.getClass().getName(), ex);
        } else if (ex != null) {
            log.warn("traceId={} status={} path={} ex={} msg={}", traceId, status.value(), path,
                    ex.getClass().getSimpleName(), truncate(ex.getMessage()));
        }

        ErrorBody body = ErrorBody.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message == null ? status.getReasonPhrase() : message)
                .path(path)
                .traceId(traceId)
                .fieldErrors(fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors)
                .build();

        return ResponseEntity.status(status).body(body);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_SAFE_MESSAGE_CHARS ? s : s.substring(0, MAX_SAFE_MESSAGE_CHARS) + "…";
    }
}
