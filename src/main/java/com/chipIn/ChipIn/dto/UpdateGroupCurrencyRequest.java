package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Body for PUT /api/groups/{groupId}/currencies/{groupCurrencyId}. Both
 * fields are optional; only non-null values are applied. masterCurrencyId
 * cannot be changed once the bucket exists (would corrupt historical
 * expenses referencing the bucket).
 */
@Data
public class UpdateGroupCurrencyRequest {

    @Size(max = 80)
    private String name;

    @DecimalMin(value = "0", inclusive = false, message = "exchangeRate must be > 0")
    @Digits(integer = 13, fraction = 6)
    private BigDecimal exchangeRate;
}
