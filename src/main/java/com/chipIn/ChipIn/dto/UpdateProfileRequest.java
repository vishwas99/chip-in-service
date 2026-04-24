package com.chipIn.ChipIn.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String name;
    private String phone;
    private String profilePicUrl;
}
