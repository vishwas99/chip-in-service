package com.chipIn.ChipIn.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateGroupRequest {
    private String name;
    private String description;
    private String imageUrl;
    private String type; // e.g., "TRIP", "HOME", "COUPLE"
    private boolean simplifyDebt = true; // Default to true
    private UUID defaultCurrencyId;
}
