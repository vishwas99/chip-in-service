package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body for POST /api/groups/{groupId}/currencies.
 * Creates a custom-named bucket (e.g. "YEN-Day1") that an expense can
 * reference. masterCurrencyId must be an active global currency.
 */
@Data
public class CreateGroupCurrencyRequest {

    @NotBlank
    @Size(max = 80)
    private String name;

    @NotNull
    private UUID masterCurrencyId;

    /**
     * 1 unit of the bucket = exchangeRate units of the master currency.
     * Must be strictly positive. Digits are bounded so we don't accidentally
     * accept absurd precision via JSON.
     */
    @NotNull
    @DecimalMin(value = "0", inclusive = false, message = "exchangeRate must be > 0")
    @Digits(integer = 13, fraction = 6)
    private BigDecimal exchangeRate;
}
