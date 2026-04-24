package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dto.CreateSettlementRequest;
import com.chipIn.ChipIn.entities.*;
import com.chipIn.ChipIn.entities.enums.ExpenseType;
import com.chipIn.ChipIn.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final ExpenseRepository expenseRepository;
    private final GroupRepository groupRepository;
    private final GroupCurrencyRepository groupCurrencyRepository;
    private final UserRepository userRepository;
    private final ExpensePayerRepository expensePayerRepository;
    private final ExpenseSplitRepository expenseSplitRepository;

    @Transactional
    public String createSettlement(CreateSettlementRequest request, User currentUser) {

        // 1. Fetch Group, Currency, and Users
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new RuntimeException("Group not found"));

        GroupCurrency currency = groupCurrencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new RuntimeException("Currency not found"));
                
        User payer = userRepository.findById(request.getPayerId())
                .orElseThrow(() -> new RuntimeException("Payer user not found"));

        User payee = userRepository.findById(request.getPayeeId())
                .orElseThrow(() -> new RuntimeException("Payee user not found"));

        // 2. Build and Save the Settlement as an Expense (Type = SETTLEMENT)
        String description = request.getNotes() != null && !request.getNotes().isEmpty() 
            ? request.getNotes() 
            : payer.getUsername() + " paid " + payee.getUsername();

        Expense settlementExpense = Expense.builder()
                .group(group)
                .createdBy(currentUser)
                .description(description)
                .amount(request.getAmount())
                .currency(currency)
                .type(ExpenseType.SETTLEMENT) // Key distinction
                .splitType("EXACT") // Settlements are always exact amounts
                .build();

        Expense savedExpense = expenseRepository.save(settlementExpense);

        // 3. Save the Payer (The person who made the payment)
        ExpensePayer expensePayer = ExpensePayer.builder()
                .expense(savedExpense)
                .user(payer)
                .paidAmount(request.getAmount())
                .build();
        expensePayerRepository.save(expensePayer);

        // 4. Save the Split (The person who received the payment 'owes' this amount to the pool to cancel out the debt)
        ExpenseSplit expenseSplit = ExpenseSplit.builder()
                .expense(savedExpense)
                .user(payee)
                .amountOwed(request.getAmount())
                .build();
        expenseSplitRepository.save(expenseSplit);

        return "Settlement created successfully with ID: " + savedExpense.getExpenseId();
    }
}
