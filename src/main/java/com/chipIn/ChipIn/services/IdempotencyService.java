package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.entities.IdempotencyKey;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Per-user idempotency for state-changing requests.
 *
 * Usage pattern from a controller:
 * <pre>
 *   return ResponseEntity.ok(idempotencyService.executeIdempotent(
 *       currentUser,
 *       idempotencyKey,
 *       "POST /api/settlements",
 *       request,
 *       SettlementResponse.class,
 *       () -> settlementService.createSettlement(request, currentUser).toResponse()
 *   ));
 * </pre>
 *
 * Semantics:
 *   - Unknown key  -> action runs, response cached for {@link #TTL}.
 *   - Same key + matching request hash -> cached response returned, action skipped.
 *   - Same key + different request hash -> 422 Unprocessable Entity.
 *   - Concurrent identical retries -> one tx wins on the unique constraint, the
 *     other rolls back (no double side-effects). Client's next retry hits cache.
 *   - Expired rows are deleted lazily on first reuse of an expired key.
 *
 * Caching scope is the SUCCESS path only. If the wrapped action throws, the
 * surrounding transaction rolls back and nothing is cached, so the client can
 * retry the same key safely.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    static final Duration TTL = Duration.ofHours(24);

    /**
     * 8-128 chars of URL-safe characters. Wide enough to allow UUIDs (36)
     * and ULIDs (26), narrow enough to reject obviously bogus payloads.
     */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_.\\-]{8,128}$");

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public <T> T executeIdempotent(
            User user,
            String idempotencyKey,
            String endpoint,
            Object requestPayload,
            Class<T> responseType,
            Supplier<T> action) {

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        validateKey(idempotencyKey);

        String requestHash = computeRequestHash(endpoint, requestPayload);
        Optional<IdempotencyKey> existingOpt = repository.findByUserIdAndIdempotencyKey(
                user.getUserid(), idempotencyKey);

        if (existingOpt.isPresent()) {
            IdempotencyKey existing = existingOpt.get();
            if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
                // Expired stub from a previous attempt. Remove it before re-executing
                // so the new attempt is a clean insert.
                repository.delete(existing);
                repository.flush();
            } else {
                if (!existing.getRequestHash().equals(requestHash)) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Idempotency-Key reused with a different request payload");
                }
                if (!endpoint.equals(existing.getEndpoint())) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Idempotency-Key reused on a different endpoint");
                }
                return deserialize(existing.getResponseBody(), existing.getResponseType(), responseType);
            }
        }

        T result = action.get();

        IdempotencyKey row = IdempotencyKey.builder()
                .userId(user.getUserid())
                .idempotencyKey(idempotencyKey)
                .endpoint(endpoint)
                .requestHash(requestHash)
                .responseStatus(200)
                .responseBody(serialize(result))
                .responseType(result == null ? responseType.getName() : result.getClass().getName())
                .expiresAt(LocalDateTime.now().plus(TTL))
                .build();

        try {
            repository.saveAndFlush(row);
        } catch (DataIntegrityViolationException race) {
            // Another concurrent request committed first under the same (user, key).
            // Our transaction's side-effects (the wrapped action) will be rolled
            // back as we re-throw. The client's next retry will read the winner's
            // cached response.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Concurrent request with the same Idempotency-Key; please retry");
        }

        return result;
    }

    private void validateKey(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be 8-128 characters of [A-Za-z0-9_.-]");
        }
    }

    /**
     * Hash of (endpoint || '|' || canonical JSON body). Endpoint is folded in
     * so reusing one key across endpoints fails the hash check explicitly.
     */
    private String computeRequestHash(String endpoint, Object payload) {
        try {
            String body = payload == null ? "" : objectMapper.writeValueAsString(payload);
            String input = endpoint + "|" + body;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not serialize request payload for idempotency hashing");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be present in every JRE.
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize idempotent response", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T deserialize(String body, String storedType, Class<T> expectedType) {
        try {
            if (storedType != null && !storedType.equals(expectedType.getName())) {
                // Same key was used on what looks like a different endpoint shape.
                // Be strict — surface a 422 rather than silently coerce types.
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Idempotency-Key replay would change response type from "
                                + storedType + " to " + expectedType.getName());
            }
            if (expectedType == String.class) {
                // Strings are serialized as JSON strings (with surrounding quotes).
                return (T) objectMapper.readValue(body, String.class);
            }
            return objectMapper.readValue(body, expectedType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not deserialize cached idempotent response", e);
        }
    }
}
