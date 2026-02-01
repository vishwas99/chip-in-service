package com.chipIn.ChipIn.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ExpensePayerDto {
    private UUID userId;
    private String userName;
    private String profilePicUrl; // Optional: Nice for UI
    private BigDecimal amountPaid;
}
