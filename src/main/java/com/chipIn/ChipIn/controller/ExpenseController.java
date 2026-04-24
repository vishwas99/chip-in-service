package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.CreateExpenseRequest;
import com.chipIn.ChipIn.dto.ExpenseDetailsResponse;
import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.services.ExpenseService;
import com.chipIn.ChipIn.util.ResponseWrapper;
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

    @PostMapping
    public ResponseEntity<String> addExpense(
            @PathVariable UUID groupId,
            @RequestBody CreateExpenseRequest request) {

        // 1. Get the current logged-in user (The creator of the expense)
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 2. Call the service to save the expense, payers, and splits
        return ResponseEntity.ok(expenseService.createExpense(groupId, request, currentUser));
    }

    @GetMapping("/{expenseId}")
    public ResponseEntity<ExpenseDetailsResponse> getExpense(@PathVariable UUID expenseId) {
        return ResponseEntity.ok(expenseService.getExpenseDetails(expenseId));
    }
}
