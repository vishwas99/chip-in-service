package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.Currency;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class GroupResponse {
    private UUID groupId;
    private String name;
    private String description;
    private String imageUrl;
    private String type;
    private boolean simplifyDebt;
    private Currency defaultCurrency;
    private LocalDateTime createdAt;
    private boolean isAdmin; // Helpful for the frontend UI
}