package com.chipIn.ChipIn.services;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Pluggable rate source for the daily FX refresh job.
 *
 * Implementations are expected to be side-effect free (read from cache,
 * upstream feed, or a static table) and to be safe to call concurrently.
 *
 * <p>Returning {@link Optional#empty()} signals "I don't have a rate for this
 * pair today" — the refresh job will leave the row alone instead of writing
 * a zero. {@link FxRateRefreshJob} surfaces the misses via metrics.
 */
public interface FxRateSource {
    /**
     * @param fromCode ISO-4217 code we're converting FROM (e.g. {@code "JPY"}).
     * @param toCode   ISO-4217 code we're converting TO (e.g. {@code "INR"}).
     * @return 1 unit of {@code from} expressed in {@code to}, or empty when
     *         the source can't quote that pair right now.
     */
    Optional<BigDecimal> getRate(String fromCode, String toCode);
}
