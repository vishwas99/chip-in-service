package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.Group;
import lombok.*;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupResponse {

    private Group group;
    private List<GroupExpenseDto> groupExpense;

}
