package com.chipIn.ChipIn.services.impl;

import com.chipIn.ChipIn.services.FxRateSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Stand-in FX source used until the real adapter (ECB / OpenExchangeRates /
 * whatever) is wired. Returns hand-curated approximate rates for a few common
 * pairs and {@link Optional#empty()} for everything else.
 *
 * Real implementation lives behind {@link FxRateSource}; swap this out by
 * dropping in a {@code @Component} that implements the same interface — the
 * {@code @ConditionalOnMissingBean} on this class will then yield.
 */
@Component
@ConditionalOnMissingBean(FxRateSource.class)
@Slf4j
public class StaticFxRateSource implements FxRateSource {

    private static final Map<String, BigDecimal> RATES = Map.ofEntries(
            // 1 unit of `from` → `to`
            Map.entry("USD->INR", new BigDecimal("83.10")),
            Map.entry("INR->USD", new BigDecimal("0.01203")),
            Map.entry("EUR->INR", new BigDecimal("90.20")),
            Map.entry("INR->EUR", new BigDecimal("0.01108")),
            Map.entry("JPY->INR", new BigDecimal("0.56")),
            Map.entry("INR->JPY", new BigDecimal("1.7857")),
            Map.entry("GBP->INR", new BigDecimal("105.00")),
            Map.entry("INR->GBP", new BigDecimal("0.00952")),
            Map.entry("USD->EUR", new BigDecimal("0.92")),
            Map.entry("EUR->USD", new BigDecimal("1.0870"))
    );

    @Override
    public Optional<BigDecimal> getRate(String fromCode, String toCode) {
        if (fromCode == null || toCode == null) return Optional.empty();
        if (fromCode.equalsIgnoreCase(toCode)) return Optional.of(BigDecimal.ONE);
        String key = fromCode.toUpperCase() + "->" + toCode.toUpperCase();
        BigDecimal rate = RATES.get(key);
        if (rate == null) {
            log.debug("StaticFxRateSource: no rate for {}", key);
            return Optional.empty();
        }
        return Optional.of(rate);
    }
}
