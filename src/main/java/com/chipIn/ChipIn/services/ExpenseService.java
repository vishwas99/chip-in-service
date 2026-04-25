package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dto.CreateExpenseRequest;
import com.chipIn.ChipIn.dto.ExpenseDetailsResponse;
import com.chipIn.ChipIn.dto.ExpensePayerDto;
import com.chipIn.ChipIn.dto.ExpenseSplitDto;
import com.chipIn.ChipIn.entities.*;
import com.chipIn.ChipIn.entities.enums.ExpenseType;
import com.chipIn.ChipIn.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CurrencyRepository currencyRepository;
    private final GroupRepository groupRepository;
    private final GroupCurrencyRepository groupCurrencyRepository;
    private final UserRepository userRepository;
    private final ExpensePayerRepository expensePayerRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final CurrencyService currencyService;

    @Transactional
    public String createExpense(UUID groupId, CreateExpenseRequest request, User currentUser) {

        log.info(request.toString());

        // 1. Validate request parameters
        if (groupId == null) {
            log.error("Expense creation failed: Group ID is null");
            throw new IllegalArgumentException("Group ID cannot be null");
        }
        if (request.getCurrencyId() == null) {
            log.error("Expense creation failed: Currency ID is null");
            throw new IllegalArgumentException("Currency ID cannot be null");
        }
        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            log.error("Expense creation failed: Description is null or empty");
            throw new IllegalArgumentException("Expense description cannot be null or empty");
        }
        if (request.getAmount() == null) {
            log.error("Expense creation failed: Amount is null");
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Expense creation failed: Invalid amount: {}", request.getAmount());
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (request.getSplitType() == null || request.getSplitType().trim().isEmpty()) {
            log.error("Expense creation failed: Split type is null or empty");
            throw new IllegalArgumentException("Split type cannot be null or empty");
        }
        if (request.getPayers() == null || request.getPayers().isEmpty()) {
            log.error("Expense creation failed: No payers provided");
            throw new IllegalArgumentException("At least one payer is required");
        }
        if (request.getSplits() == null || request.getSplits().isEmpty()) {
            log.error("Expense creation failed: No splits provided");
            throw new IllegalArgumentException("At least one split is required");
        }

        log.info("Creating expense: description={}, amount={}, payers={}, splits={}, group={}",
                 request.getDescription(), request.getAmount(), request.getPayers().size(),
                 request.getSplits().size(), groupId);

        // 2. Fetch Group
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        // 3. Validate and get the currency (handles both GroupCurrency and global Currency)
        GroupCurrency groupCurrency = currencyService.validateAndGetGroupCurrency(groupId, request.getCurrencyId(), currentUser);

        // 4. Build and Save the Main Expense
        Expense expense = Expense.builder()
                .group(group)
                .createdBy(currentUser)
                .description(request.getDescription())
                .amount(request.getAmount())
                .currency(groupCurrency)
                .type(ExpenseType.EXPENSE)
                .splitType(request.getSplitType())
                .receiptImgUrl(request.getReceiptImgUrl())
                .build();

        Expense savedExpense = expenseRepository.save(expense);

        // 5. Save ALL Payers
        List<ExpensePayer> payers = request.getPayers().stream().map(payerDto -> {
            User user = userRepository.findById(payerDto.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + payerDto.getUserId()));

            return ExpensePayer.builder()
                    .expense(savedExpense) // Link to the expense we just created
                    .user(user)
                    .paidAmount(payerDto.getPaidAmount())
                    .build();
        }).toList();

        expensePayerRepository.saveAll(payers);

        // 6. Save ALL Splits
        List<ExpenseSplit> splits = request.getSplits().stream().map(splitDto -> {
            User user = userRepository.findById(splitDto.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + splitDto.getUserId()));

            return ExpenseSplit.builder()
                    .expense(savedExpense) // Link to the expense
                    .user(user)
                    .amountOwed(splitDto.getAmountOwed())
                    .rawValue(splitDto.getRawValue())
                    .build();
        }).toList();

        expenseSplitRepository.saveAll(splits);

        return "Expense created successfully with ID: " + savedExpense.getExpenseId();
    }

    public ExpenseDetailsResponse getExpenseDetails(UUID expenseId) {
        // 1. Fetch the Expense with all relationships
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        // Map Payers
        List<ExpensePayerDto> payerDtos = expense.getPayers().stream()
                .map(p -> ExpensePayerDto.builder()
                        .userId(p.getUser().getUserid())
                        .userName(p.getUser().getUsername())
                        .amountPaid(p.getPaidAmount())
                        .build())
                .toList();

        // Map Splits
        List<ExpenseSplitDto> splitDtos = expense.getSplits().stream()
                .map(s -> ExpenseSplitDto.builder()
                        .userId(s.getUser().getUserid())
                        .userName(s.getUser().getUsername())
                        .amountOwed(s.getAmountOwed())
                        .build())
                .toList();

        // 4. Build Final Response
        return ExpenseDetailsResponse.builder()
                .expenseId(expense.getExpenseId())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .currencyCode(expense.getCurrency().getMasterCurrency().getCode()) // Get 'INR' from master
                .category(expense.getCategory())
                .type(expense.getType().toString())
                .date(expense.getCreatedAt())
                .createdByName(expense.getCreatedBy().getUsername())
                .payers(payerDtos)
                .splits(splitDtos)
                .build();
    }

    public List<Expense> getExpensesForUser(UUID userId) {
        // Step 1: Get all groups the user belongs to
        List<GroupMember> memberships = groupMemberRepository.findByIdUserId(userId);

        List<Group> groups = memberships.stream()
                .map(GroupMember::getGroup)
                .toList();

        // Step 2: Get all expenses for those groups
        return expenseRepository.findAllByGroupInAndIsDeletedFalse(groups);
    }

    public List<ExpenseSplit> getSplitsByExpenses(List<Expense> expenses) {
        return expenseSplitRepository.findByExpenseIn(expenses);
    }

}
