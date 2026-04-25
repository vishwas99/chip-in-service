package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.CreateSettlementRequest;
import com.chipIn.ChipIn.dto.SettlementResponse;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.services.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController extends BaseController {

    private final SettlementService settlementService;

    @PostMapping
    public ResponseEntity<SettlementResponse> recordSettlement(@Valid @RequestBody CreateSettlementRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        var settlement = settlementService.createSettlement(request, currentUser);

        SettlementResponse response = SettlementResponse.builder()
                .settlementId(settlement.getExpenseId())
                .message("Settlement created successfully")
                .status("SUCCESS")
                .build();

        return ResponseEntity.ok(response);
    }
}
