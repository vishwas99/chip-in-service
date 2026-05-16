package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 1, max = 120)
    private String name;

    @Pattern(regexp = "^[+0-9 ()-]{7,32}$", message = "invalid phone number")
    private String phone;

    @Size(max = 1024)
    private String profilePicUrl;
}
