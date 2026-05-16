package com.chipIn.ChipIn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateGroupRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 500)
    private String description;

    @Size(max = 1024)
    private String imageUrl;

    @NotBlank
    @Size(max = 32)
    private String type;

    private boolean simplifyDebt = true;

    @NotNull
    private UUID defaultCurrencyId;
}
