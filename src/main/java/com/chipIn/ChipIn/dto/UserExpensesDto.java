package com.chipIn.ChipIn.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UserExpensesDto {
    private UUID userId;
    private List<ExpenseResponseDto> moneyOwedList;
    private List<UserGroupResponse> userGroupResponses;

}
