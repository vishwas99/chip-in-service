package com.chipIn.ChipIn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SettlementResponse {
    private UUID settlementId;
    private String message;
    private String status; // "SUCCESS" or "FAILED"
}

