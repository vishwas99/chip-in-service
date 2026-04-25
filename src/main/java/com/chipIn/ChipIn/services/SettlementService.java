package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dto.CreateSettlementRequest;
import com.chipIn.ChipIn.entities.*;
import com.chipIn.ChipIn.entities.enums.ExpenseType;
import com.chipIn.ChipIn.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private final ExpenseRepository expenseRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final ExpensePayerRepository expensePayerRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final CurrencyService currencyService;

    @Transactional
    public Expense createSettlement(CreateSettlementRequest request, User currentUser) {

        // 1. Validate request parameters
        if (request.getGroupId() == null) {
            log.error("Settlement creation failed: Group ID is null");
            throw new IllegalArgumentException("Group ID cannot be null");
        }
        if (request.getPayerId() == null) {
            log.error("Settlement creation failed: Payer ID is null");
            throw new IllegalArgumentException("Payer ID cannot be null");
        }
        if (request.getPayeeId() == null) {
            log.error("Settlement creation failed: Payee ID is null");
            throw new IllegalArgumentException("Payee ID cannot be null");
        }
        if (request.getCurrencyId() == null) {
            log.error("Settlement creation failed: Currency ID is null");
            throw new IllegalArgumentException("Currency ID cannot be null");
        }
        if (request.getAmount() == null) {
            log.error("Settlement creation failed: Amount is null");
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Settlement creation failed: Invalid amount: {}", request.getAmount());
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        // 2. Validate that payer and payee are different
        if (request.getPayerId().equals(request.getPayeeId())) {
            log.error("Settlement creation failed: Payer and payee are the same user: {}", request.getPayerId());
            throw new IllegalArgumentException("Payer and payee must be different users");
        }

        log.info("Creating settlement: payer={}, payee={}, amount={}, group={}",
                 request.getPayerId(), request.getPayeeId(), request.getAmount(), request.getGroupId());

        // 3. Fetch Group and Users
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new RuntimeException("Group not found"));

        User payer = userRepository.findById(request.getPayerId())
                .orElseThrow(() -> new RuntimeException("Payer user not found"));

        User payee = userRepository.findById(request.getPayeeId())
                .orElseThrow(() -> new RuntimeException("Payee user not found"));

        // 4. Validate and get the currency (handles both GroupCurrency and global Currency)
        GroupCurrency currency = currencyService.validateAndGetGroupCurrency(request.getGroupId(), request.getCurrencyId(), currentUser);
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

        // 5. Save the Payer (The person who made the payment)
        ExpensePayer expensePayer = ExpensePayer.builder()
                .expense(savedExpense)
                .user(payer)
                .paidAmount(request.getAmount())
                .build();
        expensePayerRepository.save(expensePayer);

        // 6. Save the Split (The person who received the payment 'owes' this amount to the pool to cancel out the debt)
        ExpenseSplit expenseSplit = ExpenseSplit.builder()
                .expense(savedExpense)
                .user(payee)
                .amountOwed(request.getAmount())
                .build();
        expenseSplitRepository.save(expenseSplit);

        log.info("Settlement created successfully with ID: {}", savedExpense.getExpenseId());
        return savedExpense;
    }
}
