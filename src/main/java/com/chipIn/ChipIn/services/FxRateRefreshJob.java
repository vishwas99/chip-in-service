package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.GroupCurrency;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.repository.GroupCurrencyRepository;
import com.chipIn.ChipIn.repository.GroupRepository;
import com.chipIn.ChipIn.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Daily job that refreshes the FX rates ChipIn needs for the second hop of
 * the currency resolution chain (master ISO → group default).
 *
 * <p>For each group:
 *   1. Collect the distinct {@code masterCurrency} codes already referenced
 *      by the group's active currency rows.
 *   2. For every (master ≠ group.defaultCurrency) pair, ask the configured
 *      {@link FxRateSource} for a current rate.
 *   3. UPSERT a {@link GroupCurrency} FX row (active, origin=master,
 *      master=group.default). One row per (group, origin, master) — enforced
 *      by the partial unique index introduced in V2.
 *
 * <p>The job NEVER deletes rows. If the upstream source can't quote a pair
 * today, we leave the previous rate in place; the resolver will surface stale
 * rates explicitly only via the metric/log signal below.
 *
 * <p>Cron is configurable via {@code chipin.fx.refresh.cron} (default 06:00 UTC).
 * In production, wrap with {@code @SchedulerLock} (ShedLock) once we run
 * multiple instances.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FxRateRefreshJob {

    private static final String JOB_ACTOR_EMAIL = "system@chipin.local";

    private final GroupRepository groupRepository;
    private final GroupCurrencyRepository groupCurrencyRepository;
    private final UserRepository userRepository;
    private final FxRateSource fxRateSource;

    @Scheduled(cron = "${chipin.fx.refresh.cron:0 0 6 * * *}")
    @Transactional
    public void refreshAll() {
        log.info("FX refresh job starting");
        int rowsUpdated = 0, rowsInserted = 0, rowsMissed = 0;

        User actor = userRepository.findByEmail(JOB_ACTOR_EMAIL).orElse(null);
        // If a system actor doesn't exist we still proceed but skip the
        // `createdBy` column — Hibernate will reject nulls there. Document
        // this as a TODO: seed a system user during deploy.
        if (actor == null) {
            log.warn("FX refresh: no '{}' user found; insert-only path will be skipped this tick. Seed a system user to enable cold-start refresh.", JOB_ACTOR_EMAIL);
        }

        for (Group group : groupRepository.findAll()) {
            if (group.isDeleted()) continue;
            Currency groupDefault = group.getDefaultCurrency();
            if (groupDefault == null) continue;

            // Collect every master currency this group cares about.
            List<GroupCurrency> rows = groupCurrencyRepository.findActiveByGroupId(group.getGroupId());
            Set<Currency> masters = new HashSet<>();
            for (GroupCurrency r : rows) {
                if (r.getMasterCurrency() != null) masters.add(r.getMasterCurrency());
                if (r.getOriginCurrency() != null) masters.add(r.getOriginCurrency());
            }
            masters.remove(groupDefault);

            for (Currency master : masters) {
                Optional<BigDecimal> rate = fxRateSource.getRate(master.getCode(), groupDefault.getCode());
                if (rate.isEmpty()) {
                    rowsMissed++;
                    log.info("FX refresh: no rate for groupId={} pair={}->{}",
                            group.getGroupId(), master.getCode(), groupDefault.getCode());
                    continue;
                }

                List<GroupCurrency> existing = groupCurrencyRepository.findFxCandidates(
                        group.getGroupId(), master.getCurrencyId(), groupDefault.getCurrencyId());

                if (!existing.isEmpty()) {
                    GroupCurrency row = existing.get(0);
                    row.setExchangeRate(rate.get());
                    groupCurrencyRepository.save(row);
                    rowsUpdated++;
                } else if (actor != null) {
                    GroupCurrency row = GroupCurrency.builder()
                            .group(group)
                            .originCurrency(master)
                            .masterCurrency(groupDefault)
                            .name("FX " + master.getCode() + "->" + groupDefault.getCode())
                            .exchangeRate(rate.get())
                            .createdBy(actor)
                            .createdAt(LocalDateTime.now())
                            .isActive(true)
                            .build();
                    groupCurrencyRepository.save(row);
                    rowsInserted++;
                }
            }
        }

        log.info("FX refresh job finished inserted={} updated={} missed={}",
                rowsInserted, rowsUpdated, rowsMissed);
    }
}
