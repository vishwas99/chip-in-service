package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Periodically vacuums expired rows from {@code idempotency_keys}.
 *
 * Rows are also pruned lazily on the next request that reuses an expired
 * {@code (user_id, key)} pair — so the table never grows without bound — but
 * this job keeps it tidy in the steady state.
 *
 * <p>The cron expression is configurable via {@code chipin.idempotency.cleanup.cron}.
 * Default is every 15 minutes.
 *
 * <p>When the service is scaled to multiple instances, wrap this method in
 * {@code @SchedulerLock} (ShedLock) so only one instance runs the cleanup per
 * tick. We're single-instance today, so straight {@code @Scheduled} suffices.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupJob {

    private final IdempotencyKeyRepository repository;

    @Scheduled(cron = "${chipin.idempotency.cleanup.cron:0 */15 * * * *}")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteAllExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("idempotency cleanup: deleted {} expired row(s)", deleted);
        }
    }
}
