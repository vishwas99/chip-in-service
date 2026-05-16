package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PayerRequest {
    @NotNull
    private UUID userId;

    @NotNull
    @DecimalMin(value = "0", inclusive = false, message = "paidAmount must be > 0")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal paidAmount;
}
