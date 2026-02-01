package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dto.CreateExpenseRequest;
import com.chipIn.ChipIn.dto.ExpenseDetailsResponse;
import com.chipIn.ChipIn.dto.ExpensePayerDto;
import com.chipIn.ChipIn.dto.ExpenseSplitDto;
import com.chipIn.ChipIn.entities.*;
import com.chipIn.ChipIn.entities.enums.ExpenseType;
import com.chipIn.ChipIn.repository.*;
import jakarta.transaction.TransactionScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CurrencyRepository currencyRepository;
    private final GroupRepository groupRepository;
    private final GroupCurrencyRepository groupCurrencyRepository;
    private final UserRepository userRepository;
    private final ExpensePayerRepository expensePayerRepository;
    private final ExpenseSplitRepository expenseSplitRepository;

    @Transactional
    public String createExpense(UUID groupId, CreateExpenseRequest request, User currentUser){

        // 1. Fetch Group and Currency
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        GroupCurrency currency = groupCurrencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new RuntimeException("Currency not found"));

        // 2. Build and Save the Main Expense
        Expense expense = Expense.builder()
                .group(group)
                .createdBy(currentUser)
                .description(request.getDescription())
                .amount(request.getAmount())
                .currency(currency) // ✅ Fixed
                .type(ExpenseType.EXPENSE)
                .splitType(request.getSplitType())
                .receiptImgUrl(request.getReceiptImgUrl())
                .build();

        Expense savedExpense = expenseRepository.save(expense);

        // 3. Save ALL Payers
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

        // 4. Save ALL Splits
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

}
