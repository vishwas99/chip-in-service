package com.chipIn.ChipIn.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class CreateExpenseRequest {
    private String description;

    // Total amount in the specific currency (e.g., 5000 Yen)
    private BigDecimal amount;

    // The ID of the Master Currency (e.g., INR, USD) OR the Custom Group Currency
    private UUID currencyId;

    // Optional categorization: "FOOD", "TRAVEL", "HOTEL", etc.
    private String type;

    // "EQUAL", "PERCENTAGE", "EXACT", "SHARES"
    private String splitType;

    private String receiptImgUrl;

    // The lists of financial distribution
    private List<PayerRequest> payers;
    private List<SplitRequest> splits;
}