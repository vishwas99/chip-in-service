package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.CreateExpenseRequest;
import com.chipIn.ChipIn.dto.ExpenseDetailsResponse;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.services.ExpenseService;
import com.chipIn.ChipIn.services.IdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
@RequiredArgsConstructor
public class ExpenseController extends BaseController {

    private final ExpenseService expenseService;
    private final IdempotencyService idempotencyService;

    @PostMapping
    public ResponseEntity<String> addExpense(
            @PathVariable UUID groupId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateExpenseRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // Endpoint id includes the groupId so the same key cannot be replayed
        // across groups by mistake.
        String endpointId = "POST /api/groups/" + groupId + "/expenses";

        String expenseId = idempotencyService.executeIdempotent(
                currentUser,
                idempotencyKey,
                endpointId,
                request,
                String.class,
                () -> expenseService.createExpense(groupId, request, currentUser));

        return ResponseEntity.ok(expenseId);
    }

    @GetMapping("/{expenseId}")
    public ResponseEntity<ExpenseDetailsResponse> getExpense(
            @PathVariable UUID groupId,
            @PathVariable UUID expenseId) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(expenseService.getExpenseDetails(groupId, expenseId, currentUser));
    }
}
