package com.chipIn.ChipIn.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ExpenseDetailsResponse {
    private UUID expenseId;
    private String description;
    private BigDecimal amount;
    private String currencyCode;
    private String category;
    private String type;
    private LocalDateTime date;
    private String createdByName;

    // Reusing the standalone DTOs
    private List<ExpensePayerDto> payers;
    private List<ExpenseSplitDto> splits;
}
