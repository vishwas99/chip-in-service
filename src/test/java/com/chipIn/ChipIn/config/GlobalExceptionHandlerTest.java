package com.chipIn.ChipIn.config;

import com.chipIn.ChipIn.dto.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the most important property of {@link GlobalExceptionHandler}:
 * <em>no stack-trace text leaks into the response body</em>.
 *
 * If you add a new {@code @ExceptionHandler}, add a case here so the
 * "do not leak ex.getMessage()" contract stays enforced.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleIllegalArgument_returns400WithItsMessage() {
        HttpServletRequest req = mockReq("/api/groups");
        ResponseEntity<ErrorBody> resp = handler.handleIllegalArgument(
                new IllegalArgumentException("Amount must be positive"), req);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getMessage()).isEqualTo("Amount must be positive");
        assertThat(resp.getBody().getTraceId()).isNotBlank();
        assertThat(resp.getBody().getPath()).isEqualTo("/api/groups");
        assertThat(resp.getBody().getFieldErrors()).isNull();
    }

    @Test
    void handleAccessDenied_returns403Generic() {
        ResponseEntity<ErrorBody> resp = handler.handleAccessDenied(
                new AccessDeniedException("user 123 cannot see group 456"), mockReq("/x"));

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        // Must not echo the raw exception message — that could leak identifiers.
        assertThat(resp.getBody().getMessage()).isEqualTo("Access denied");
    }

    @Test
    void handleOptimisticLock_returns409GenericRetryHint() {
        ResponseEntity<ErrorBody> resp = handler.handleOptimisticLock(
                new OptimisticLockingFailureException("row was bumped"), mockReq("/x"));

        assertThat(resp.getStatusCode().value()).isEqualTo(409);
        assertThat(resp.getBody().getMessage()).contains("retry");
        assertThat(resp.getBody().getMessage()).doesNotContain("bumped");
    }

    @Test
    void handleUnexpected_returns500WithoutLeakingMessage() {
        ResponseEntity<ErrorBody> resp = handler.handleUnexpected(
                new RuntimeException("DB password is super-secret-123"), mockReq("/x"));

        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(resp.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(resp.getBody().getMessage()).doesNotContain("super-secret");
        assertThat(resp.getBody().getTraceId()).isNotBlank();
    }

    @Test
    void handleResponseStatus_passesThroughReason() {
        ResponseEntity<ErrorBody> resp = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"), mockReq("/x"));

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        assertThat(resp.getBody().getMessage()).isEqualTo("Group not found");
    }

    @Test
    void handleConstraintViolation_returnsFieldErrors() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<jakarta.validation.ConstraintViolation<Bean>> v = validator.validate(new Bean(""));
        assertThat(v).isNotEmpty();
        ConstraintViolationException ex = new ConstraintViolationException(v);

        ResponseEntity<ErrorBody> resp = handler.handleConstraintViolation(ex, mockReq("/x"));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().getFieldErrors()).isNotEmpty();
        assertThat(resp.getBody().getFieldErrors().get(0).getField()).contains("name");
    }

    private static HttpServletRequest mockReq(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(uri);
        return req;
    }

    static class Bean {
        @NotBlank(message = "name must not be blank")
        String name;
        Bean(String name) { this.name = name; }
    }
}
