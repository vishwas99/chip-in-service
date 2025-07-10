package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.Group;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class UserGroupResponse {

    private Group group;
    private float moneyOwed;

}
