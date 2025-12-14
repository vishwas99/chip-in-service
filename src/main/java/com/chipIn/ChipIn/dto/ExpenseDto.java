package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.Split;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
public class ExpenseDto {

    @NotNull(message = "Group ID cannot be null")
    private UUID groupId;

    @NotNull(message = "Expense owner cannot be null")
    private UUID expenseOwner;

    @NotNull(message = "Paid by cannot be null")
    @Positive(message = "Paid by must be a positive number and >0")
    private float amount;

    private String description;

    @NotNull
    @Size(min = 2, max = 20, message = "Expense name must be between 2-100 characters")
    private String expenseName;

    @NotNull(message = "Expense split cannot be null")
    private List<SplitDto> expenseSplit;

    @NotNull(message = "Currency is not provided")
    private UUID currencyId;

    public Expense toEntity() {
        Expense expense = new Expense();
        expense.setName(this.expenseName);
        expense.setPaidBy(this.expenseOwner);
        expense.setAmount(this.amount);
        expense.setDescription(this.description);
        expense.setGroupId(this.groupId);
        expense.setDate(LocalDateTime.now());
        expense.setCurrencyId(this.currencyId);
        List<Split> splits = new ArrayList<>();
        for (SplitDto splitDto : this.expenseSplit) {
            Split newSplit = splitDto.toEntity();
            newSplit.setExpense(expense);
            splits.add(newSplit);
        }
        expense.setSplits(splits);
        return expense;
    }

    @Override
    public String toString() {
        return "ExpenseDto{" +
                "groupId=" + groupId +
                ", expenseOwner=" + expenseOwner +
                ", amount=" + amount +
                ", description='" + description + '\'' +
                ", expenseName='" + expenseName + '\'' +
                ", expenseSplit=" + expenseSplit +
                '}';
    }

}




