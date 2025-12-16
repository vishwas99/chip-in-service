package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.Currency;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ExpenseResponseDto {

    public Float moneyOwed;
    public String currency;


}
