package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.Split;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ExpenseDetailsDto {
    private Expense expense;
    private List<Split> splits;
}
