package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.Group;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class GroupDataByUserResponse {

    private Group group;
    private Double amountOwedByUser;
    private LocalDateTime lastExpenseDate;

}
