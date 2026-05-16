package com.chipIn.ChipIn.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records a per-user idempotency token for state-changing requests so that
 * client retries (network blips, double-taps, app crashes mid-POST) do not
 * create duplicate financial records.
 *
 * Unique on (user_id, idempotency_key). Two parallel POSTs with the same key
 * therefore race on the constraint; the loser rolls back its whole transaction
 * (including the side-effects of the wrapped action) and the next retry
 * receives the winner's stored response.
 *
 * A separate scheduled job (out of scope here) is expected to delete rows
 * older than `expires_at` periodically. Until then, expired rows are deleted
 * lazily on the next request that references them.
 */
@Entity
@Table(
        name = "idempotency_keys",
        schema = "chip_in_core",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_idempotency_user_key",
                columnNames = {"user_id", "idempotency_key"}
        ),
        indexes = {
                @Index(name = "idx_idempotency_expires_at", columnList = "expires_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    /** Logical endpoint identifier ("POST /api/settlements" etc.). Hashed into
     *  request_hash so reusing one key across endpoints produces a mismatch. */
    @Column(name = "endpoint", nullable = false, length = 256)
    private String endpoint;

    /** SHA-256 of the canonical (endpoint + JSON body) tuple. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    /** Serialized JSON of the original response body. */
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    /** Fully-qualified class name of the response body, used to safely
     *  deserialize on replay. */
    @Column(name = "response_type", length = 256)
    private String responseType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
