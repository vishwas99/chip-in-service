package com.chipIn.ChipIn.repository;

import com.chipIn.ChipIn.entities.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    Optional<IdempotencyKey> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.expiresAt < :cutoff")
    int deleteAllExpired(@Param("cutoff") LocalDateTime cutoff);
}
