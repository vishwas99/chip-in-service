package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserToGroupDto {
    @NotBlank
    private UUID userId;
    @NotBlank
    private UUID groupId;
}
