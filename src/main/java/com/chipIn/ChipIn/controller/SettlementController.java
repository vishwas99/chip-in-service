package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.CreateSettlementRequest;
import com.chipIn.ChipIn.dto.SettlementResponse;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.services.IdempotencyService;
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

    private static final String ENDPOINT_ID = "POST /api/settlements";

    private final SettlementService settlementService;
    private final IdempotencyService idempotencyService;

    @PostMapping
    public ResponseEntity<SettlementResponse> recordSettlement(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateSettlementRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        SettlementResponse response = idempotencyService.executeIdempotent(
                currentUser,
                idempotencyKey,
                ENDPOINT_ID,
                request,
                SettlementResponse.class,
                () -> {
                    var settlement = settlementService.createSettlement(request, currentUser);
                    return SettlementResponse.builder()
                            .settlementId(settlement.getExpenseId())
                            .message("Settlement created successfully")
                            .status("SUCCESS")
                            .build();
                });

        return ResponseEntity.ok(response);
    }
}
