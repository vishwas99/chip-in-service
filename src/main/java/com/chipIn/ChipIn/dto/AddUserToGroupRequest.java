package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddUserToGroupRequest {
    @Email
    @NotNull
    private String email;

    @NotNull
    private UUID groupId;
}
