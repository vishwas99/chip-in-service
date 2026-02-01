package com.chipIn.ChipIn.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PayerRequest {
    private UUID userId;

    // How much this specific user paid
    private BigDecimal paidAmount;
}
