package com.chipIn.ChipIn.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ExpenseSplitDto {
    private UUID userId;
    private String userName;
    private String profilePicUrl;
    private BigDecimal amountOwed;
}
