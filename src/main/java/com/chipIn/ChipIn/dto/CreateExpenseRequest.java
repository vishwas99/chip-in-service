package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.enums.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class CreateExpenseRequest {

    @NotBlank
    @Size(max = 255)
    private String description;

    @NotNull
    @DecimalMin(value = "0", inclusive = false, message = "amount must be > 0")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @NotNull
    private UUID currencyId;

    /** Optional category label (e.g. FOOD, TRAVEL). */
    @Size(max = 64)
    private String category;

    @NotNull
    private SplitType splitType;

    @Size(max = 1024)
    private String receiptImgUrl;

    @NotEmpty
    @Valid
    private List<PayerRequest> payers;

    @NotEmpty
    @Valid
    private List<SplitRequest> splits;
}
