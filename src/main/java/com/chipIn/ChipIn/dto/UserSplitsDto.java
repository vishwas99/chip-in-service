package com.chipIn.ChipIn.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserSplitsDto {

    private UserDto user;
    private List<UserSplitHelperDto> expenseList;

}
