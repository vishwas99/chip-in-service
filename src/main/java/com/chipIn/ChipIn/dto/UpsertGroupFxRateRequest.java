package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body for PUT /api/groups/{groupId}/fx-rates. Idempotent: writing the same
 * (fromCurrencyId, toCurrencyId) pair updates the existing FX row instead
 * of creating a duplicate. Used by both admin UI and the future daily refresh
 * job.
 */
@Data
public class UpsertGroupFxRateRequest {

    @NotNull
    private UUID fromCurrencyId;

    @NotNull
    private UUID toCurrencyId;

    @NotNull
    @DecimalMin(value = "0", inclusive = false, message = "rate must be > 0")
    @Digits(integer = 13, fraction = 6)
    private BigDecimal rate;
}
