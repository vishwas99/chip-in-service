package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.Split;
import lombok.Data;

import java.util.UUID;

@Data
public class SplitDto {

    private UUID userId;
    private Float amount;

    public Split toEntity() {
        Split split = new Split();
        split.setUserId(this.userId);
        split.setAmountOwed(this.amount);
        return split;
    }

}
