package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.Currency;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MoneyOwedDto {
    public Float moneyOwed;
    public Currency currency;
}
