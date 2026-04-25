package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@ToString
public class CreateSettlementRequest {
    @NotNull
    private UUID groupId;

    @NotNull
    private UUID payerId; // The user making the payment

    @NotNull
    private UUID payeeId; // The user receiving the payment

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    private UUID currencyId; // The ID of the currency used for the settlement
    
    private String notes;
}
