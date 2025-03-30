package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class UserToGroupDto {
    @NotBlank
    private UUID userId;
    @NotBlank
    private UUID groupId;
}
