package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.Group;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GroupDataByUserResponse {
    private Group group;
    /** In the group's default currency. */
    private BigDecimal amountOwedByUser;
    private LocalDateTime lastExpenseDate;
}
