package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddMemberRequest {
    @Email
    @NotBlank
    private String email;

    private boolean isAdmin = false;
}
