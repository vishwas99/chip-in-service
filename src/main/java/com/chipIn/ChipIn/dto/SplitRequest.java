package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NonNull;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class SplitRequest {
    @NonNull
    private UUID userId;

    // The final calculated share of the expense for this user
    @Min(0)
    private BigDecimal amountOwed;

    // Useful for PERCENTAGE/SHARES.
    // e.g., if splitType is PERCENTAGE, rawValue = 20.0 (for 20%)
    @Min(0)
    private BigDecimal rawValue;
}
