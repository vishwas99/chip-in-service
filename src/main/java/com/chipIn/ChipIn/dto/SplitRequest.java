package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row in the split list. `amountOwed` is required for EXACT splits but
 * the server recomputes it for EQUAL/PERCENTAGE/SHARES — see
 * `ExpenseService.normaliseSplits`. `rawValue` carries percent / share counts
 * for those types and is ignored for EXACT.
 */
@Data
public class SplitRequest {
    @NotNull
    private UUID userId;

    @DecimalMin(value = "0", message = "amountOwed must be >= 0")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amountOwed;

    @DecimalMin(value = "0", message = "rawValue must be >= 0")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal rawValue;
}
