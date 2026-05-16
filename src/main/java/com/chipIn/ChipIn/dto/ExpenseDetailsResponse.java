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

    /** Amount as entered, in the bucket currency. */
    private BigDecimal amount;
    /** Display name of the bucket (e.g. "YEN-Day1" or "Base INR"). */
    private String bucketName;
    /** The bucket->master rate at the time of entry. */
    private BigDecimal bucketRate;
    /** ISO code of the master currency the bucket maps into. */
    private String masterCurrencyCode;
    /** Same amount projected into the group's default currency (null if FX missing). */
    private BigDecimal amountInGroupDefault;
    private String groupCurrencyCode;

    private String category;
    private String type;
    private LocalDateTime date;
    private String createdByName;

    private List<ExpensePayerDto> payers;
    private List<ExpenseSplitDto> splits;
}
