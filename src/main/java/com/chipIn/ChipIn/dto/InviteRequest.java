package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class InviteRequest {
    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String name;

    private UUID groupId; // Optional: if inviting directly to a group
}
