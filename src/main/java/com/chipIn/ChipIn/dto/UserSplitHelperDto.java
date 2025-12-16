package com.chipIn.ChipIn.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSplitHelperDto {
    private UserDto user;
    private List<MoneyOwedDto> moneyOwed;
}
