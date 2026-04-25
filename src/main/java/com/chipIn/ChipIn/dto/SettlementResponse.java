package com.chipIn.ChipIn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class SettlementResponse {
    private UUID settlementId;
    private String message;
    private String status; // "SUCCESS" or "FAILED"
}

