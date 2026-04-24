package com.chipIn.ChipIn.dto;

import lombok.Data;

import java.util.List;

@Data
public class GroupsTabResponse {
    private List<GroupDataByUserResponse> groups;
}
