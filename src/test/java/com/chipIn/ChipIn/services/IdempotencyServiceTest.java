package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.entities.IdempotencyKey;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {

    private IdempotencyKeyRepository repository;
    private ObjectMapper objectMapper;
    private IdempotencyService service;

    private User user;
    private final String endpoint = "POST /api/settlements";
    private final String key = "11111111-2222-3333-4444-555555555555";

    @BeforeEach
    void setUp() {
        repository = mock(IdempotencyKeyRepository.class);
        objectMapper = new ObjectMapper();
        service = new IdempotencyService(repository, objectMapper);

        user = new User();
        user.setUserid(UUID.randomUUID());
        user.setName("Test User");
    }

    @Test
    void executesActionAndPersistsCacheOnFirstCall() {
        when(repository.findByUserIdAndIdempotencyKey(user.getUserid(), key))
                .thenReturn(Optional.empty());

        Payload payload = new Payload("foo", 10);
        AtomicInteger executions = new AtomicInteger();

        String result = service.executeIdempotent(user, key, endpoint, payload, String.class,
                () -> {
                    executions.incrementAndGet();
                    return "expense-123";
                });

        assertEquals("expense-123", result);
        assertEquals(1, executions.get());

        ArgumentCaptor<IdempotencyKey> savedCaptor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(repository).saveAndFlush(savedCaptor.capture());
        IdempotencyKey saved = savedCaptor.getValue();
        assertEquals(user.getUserid(), saved.getUserId());
        assertEquals(key, saved.getIdempotencyKey());
        assertEquals(endpoint, saved.getEndpoint());
        assertEquals(64, saved.getRequestHash().length(), "SHA-256 hex should be 64 chars");
        assertEquals(200, saved.getResponseStatus());
    }

    @Test
    void replaysCachedResponseWithoutRunningAction() {
        Payload payload = new Payload("foo", 10);

        // Pre-compute what the service would store for this payload by running once
        // against a fresh mock, then return that row on the second call.
        IdempotencyKey stored = IdempotencyKey.builder()
                .userId(user.getUserid())
                .idempotencyKey(key)
                .endpoint(endpoint)
                .requestHash(hashFor(endpoint, payload))
                .responseStatus(200)
                .responseBody("\"expense-123\"")
                .responseType(String.class.getName())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(repository.findByUserIdAndIdempotencyKey(user.getUserid(), key))
                .thenReturn(Optional.of(stored));

        AtomicInteger executions = new AtomicInteger();
        String result = service.executeIdempotent(user, key, endpoint, payload, String.class,
                () -> {
                    executions.incrementAndGet();
                    return "should-not-run";
                });

        assertEquals("expense-123", result);
        assertEquals(0, executions.get(), "Action must not run on replay");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsKeyReuseWithDifferentPayload() {
        Payload original = new Payload("foo", 10);
        Payload tampered = new Payload("foo", 9999);

        IdempotencyKey stored = IdempotencyKey.builder()
                .userId(user.getUserid())
                .idempotencyKey(key)
                .endpoint(endpoint)
                .requestHash(hashFor(endpoint, original))
                .responseStatus(200)
                .responseBody("\"expense-123\"")
                .responseType(String.class.getName())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(repository.findByUserIdAndIdempotencyKey(user.getUserid(), key))
                .thenReturn(Optional.of(stored));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.executeIdempotent(user, key, endpoint, tampered, String.class,
                        () -> "should-not-run"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    @Test
    void rejectsKeyReuseAcrossEndpoints() {
        Payload payload = new Payload("foo", 10);
        IdempotencyKey stored = IdempotencyKey.builder()
                .userId(user.getUserid())
                .idempotencyKey(key)
                .endpoint("POST /api/groups/abc/expenses")
                .requestHash(hashFor("POST /api/groups/abc/expenses", payload))
                .responseStatus(200)
                .responseBody("\"expense-xyz\"")
                .responseType(String.class.getName())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(repository.findByUserIdAndIdempotencyKey(user.getUserid(), key))
                .thenReturn(Optional.of(stored));

        // Same key, different endpoint and payload combination -> hash mismatch first.
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.executeIdempotent(user, key, endpoint, payload, String.class,
                        () -> "should-not-run"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    @Test
    void rejectsMalformedKey() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.executeIdempotent(user, "short", endpoint, null, String.class,
                        () -> "x"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        ex = assertThrows(ResponseStatusException.class,
                () -> service.executeIdempotent(user, null, endpoint, null, String.class,
                        () -> "x"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void translatesRaceToConflict() {
        when(repository.findByUserIdAndIdempotencyKey(user.getUserid(), key))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.executeIdempotent(user, key, endpoint,
                        new Payload("foo", 10), String.class, () -> "expense-123"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void expiredRowIsClearedAndActionReruns() {
        Payload payload = new Payload("foo", 10);
        IdempotencyKey expired = IdempotencyKey.builder()
                .userId(user.getUserid())
                .idempotencyKey(key)
                .endpoint(endpoint)
                .requestHash(hashFor(endpoint, payload))
                .responseStatus(200)
                .responseBody("\"expense-old\"")
                .responseType(String.class.getName())
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(repository.findByUserIdAndIdempotencyKey(user.getUserid(), key))
                .thenReturn(Optional.of(expired));

        AtomicInteger executions = new AtomicInteger();
        String result = service.executeIdempotent(user, key, endpoint, payload, String.class,
                () -> {
                    executions.incrementAndGet();
                    return "expense-fresh";
                });

        assertEquals("expense-fresh", result);
        assertEquals(1, executions.get());
        verify(repository).delete(expired);
        verify(repository).saveAndFlush(any());
    }

    /** Computes the same canonical hash the service would compute, for test fixtures. */
    private String hashFor(String endpoint, Object payload) {
        try {
            String body = payload == null ? "" : objectMapper.writeValueAsString(payload);
            String input = endpoint + "|" + body;
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Trivial JSON-serializable payload for hashing tests. */
    public static class Payload {
        public String description;
        public int amount;

        public Payload() {}

        public Payload(String description, int amount) {
            this.description = description;
            this.amount = amount;
        }

        public String getDescription() { return description; }
        public int getAmount() { return amount; }
    }
}
