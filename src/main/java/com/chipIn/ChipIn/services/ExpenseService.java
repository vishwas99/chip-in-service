package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dto.CreateExpenseRequest;
import com.chipIn.ChipIn.dto.ExpenseDetailsResponse;
import com.chipIn.ChipIn.dto.ExpensePayerDto;
import com.chipIn.ChipIn.dto.ExpenseSplitDto;
import com.chipIn.ChipIn.dto.PayerRequest;
import com.chipIn.ChipIn.dto.SplitRequest;
import com.chipIn.ChipIn.entities.*;
import com.chipIn.ChipIn.entities.enums.ExpenseType;
import com.chipIn.ChipIn.entities.enums.SplitType;
import com.chipIn.ChipIn.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {

    /** Tolerance for floating-point reconciliation between payers / splits / amount. */
    private static final BigDecimal SUM_TOLERANCE = new BigDecimal("0.01");
    private static final int MONEY_SCALE = 4;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_EVEN;

    private final ExpenseRepository expenseRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final ExpensePayerRepository expensePayerRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final CurrencyService currencyService;
    private final CurrencyResolutionService currencyResolutionService;
    private final AccessGuard accessGuard;

    @Transactional
    public String createExpense(UUID groupId, CreateExpenseRequest request, User currentUser) {
        Group group = accessGuard.requireGroup(groupId);
        accessGuard.requireGroupMember(group, currentUser);

        if (request.getPayers() == null || request.getPayers().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one payer is required");
        }
        if (request.getSplits() == null || request.getSplits().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one split is required");
        }

        Set<UUID> memberIds = groupMembersOf(groupId);
        validateAllPayersAreMembers(request.getPayers(), memberIds);
        validateAllSplittersAreMembers(request.getSplits(), memberIds);

        validatePayerSum(request.getPayers(), request.getAmount());
        List<SplitRequest> normalisedSplits = normaliseSplits(request.getSplitType(), request.getSplits(), request.getAmount());

        GroupCurrency bucket = currencyService.validateAndGetGroupCurrency(groupId, request.getCurrencyId(), currentUser);

        Expense expense = Expense.builder()
                .group(group)
                .createdBy(currentUser)
                .description(request.getDescription())
                .amount(request.getAmount().setScale(MONEY_SCALE, MONEY_ROUNDING))
                .currency(bucket)
                .type(ExpenseType.EXPENSE)
                .splitType(request.getSplitType())
                .receiptImgUrl(request.getReceiptImgUrl())
                .category(request.getCategory())
                .build();

        Expense savedExpense = expenseRepository.save(expense);

        List<ExpensePayer> payers = new ArrayList<>(request.getPayers().size());
        for (PayerRequest payerDto : request.getPayers()) {
            User user = userRepository.findById(payerDto.getUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Payer not found: " + payerDto.getUserId()));
            payers.add(ExpensePayer.builder()
                    .expense(savedExpense)
                    .user(user)
                    .paidAmount(payerDto.getPaidAmount().setScale(MONEY_SCALE, MONEY_ROUNDING))
                    .build());
        }
        expensePayerRepository.saveAll(payers);

        List<ExpenseSplit> splits = new ArrayList<>(normalisedSplits.size());
        for (SplitRequest splitDto : normalisedSplits) {
            User user = userRepository.findById(splitDto.getUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Splitter not found: " + splitDto.getUserId()));
            splits.add(ExpenseSplit.builder()
                    .expense(savedExpense)
                    .user(user)
                    .amountOwed(splitDto.getAmountOwed().setScale(MONEY_SCALE, MONEY_ROUNDING))
                    .rawValue(splitDto.getRawValue())
                    .build());
        }
        expenseSplitRepository.saveAll(splits);

        log.info("Created expense {} in group {} (amount={}, type={}, split={})",
                savedExpense.getExpenseId(), groupId, savedExpense.getAmount(),
                savedExpense.getType(), savedExpense.getSplitType());

        return savedExpense.getExpenseId().toString();
    }

    @Transactional(readOnly = true)
    public ExpenseDetailsResponse getExpenseDetails(UUID groupId, UUID expenseId, User currentUser) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));

        if (groupId != null && !expense.getGroup().getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found");
        }
        accessGuard.requireGroupMember(expense.getGroup(), currentUser);

        List<ExpensePayerDto> payerDtos = expense.getPayers().stream()
                .map(p -> ExpensePayerDto.builder()
                        .userId(p.getUser().getUserid())
                        .userName(p.getUser().getUsername())
                        .amountPaid(p.getPaidAmount())
                        .build())
                .toList();

        List<ExpenseSplitDto> splitDtos = expense.getSplits().stream()
                .map(s -> ExpenseSplitDto.builder()
                        .userId(s.getUser().getUserid())
                        .userName(s.getUser().getUsername())
                        .amountOwed(s.getAmountOwed())
                        .build())
                .toList();

        BigDecimal totalInGroupDefault = currencyResolutionService.expenseTotalInGroupDefault(expense);
        Currency master = expense.getCurrency().getMasterCurrency();

        return ExpenseDetailsResponse.builder()
                .expenseId(expense.getExpenseId())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .bucketName(expense.getCurrency().getName())
                .bucketRate(expense.getCurrency().getExchangeRate())
                .masterCurrencyCode(master.getCode())
                .amountInGroupDefault(totalInGroupDefault == null ? null
                        : totalInGroupDefault.setScale(2, MONEY_ROUNDING))
                .groupCurrencyCode(expense.getGroup().getDefaultCurrency().getCode())
                .category(expense.getCategory())
                .type(expense.getType().toString())
                .date(expense.getCreatedAt())
                .createdByName(expense.getCreatedBy().getName())
                .payers(payerDtos)
                .splits(splitDtos)
                .build();
    }

    @Transactional(readOnly = true)
    public List<Expense> getExpensesForUser(UUID userId) {
        List<GroupMember> memberships = groupMemberRepository.findByIdUserId(userId);
        List<Group> groups = memberships.stream().map(GroupMember::getGroup).toList();
        if (groups.isEmpty()) {
            return List.of();
        }
        return expenseRepository.findAllByGroupInAndIsDeletedFalse(groups);
    }

    @Transactional(readOnly = true)
    public List<ExpenseSplit> getSplitsByExpenses(List<Expense> expenses) {
        if (expenses == null || expenses.isEmpty()) return List.of();
        return expenseSplitRepository.findByExpenseIn(expenses);
    }

    // ------------------------------ helpers ------------------------------

    private Set<UUID> groupMembersOf(UUID groupId) {
        return groupMemberRepository.findByGroupGroupId(groupId).stream()
                .map(m -> m.getUser().getUserid())
                .collect(HashSet::new, Set::add, Set::addAll);
    }

    private void validateAllPayersAreMembers(List<PayerRequest> payers, Set<UUID> memberIds) {
        for (PayerRequest p : payers) {
            if (!memberIds.contains(p.getUserId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Payer " + p.getUserId() + " is not a member of the group");
            }
        }
    }

    private void validateAllSplittersAreMembers(List<SplitRequest> splits, Set<UUID> memberIds) {
        for (SplitRequest s : splits) {
            if (!memberIds.contains(s.getUserId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Splitter " + s.getUserId() + " is not a member of the group");
            }
        }
    }

    private void validatePayerSum(List<PayerRequest> payers, BigDecimal amount) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PayerRequest p : payers) sum = sum.add(p.getPaidAmount());
        if (sum.subtract(amount).abs().compareTo(SUM_TOLERANCE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sum of payer amounts (" + sum + ") does not equal total expense amount (" + amount + ")");
        }
    }

    /**
     * Recomputes `amountOwed` from `rawValue` for EQUAL / PERCENTAGE / SHARES.
     * For EXACT the client-supplied `amountOwed` values are kept but must sum
     * to `amount` (within tolerance). Always validates that the resulting
     * total matches `amount`.
     */
    private List<SplitRequest> normaliseSplits(SplitType splitType, List<SplitRequest> splits, BigDecimal amount) {
        List<SplitRequest> result = new ArrayList<>(splits.size());

        switch (splitType) {
            case EQUAL -> {
                BigDecimal share = amount.divide(BigDecimal.valueOf(splits.size()), MONEY_SCALE, MONEY_ROUNDING);
                BigDecimal running = BigDecimal.ZERO;
                for (int i = 0; i < splits.size(); i++) {
                    SplitRequest in = splits.get(i);
                    SplitRequest out = new SplitRequest();
                    out.setUserId(in.getUserId());
                    BigDecimal owed = (i == splits.size() - 1) ? amount.subtract(running) : share;
                    out.setAmountOwed(owed.setScale(MONEY_SCALE, MONEY_ROUNDING));
                    running = running.add(owed);
                    result.add(out);
                }
            }
            case PERCENTAGE -> {
                BigDecimal totalPercent = splits.stream()
                        .map(s -> s.getRawValue() == null ? BigDecimal.ZERO : s.getRawValue())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (totalPercent.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.01")) > 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Split percentages must sum to 100 (got " + totalPercent + ")");
                }
                BigDecimal running = BigDecimal.ZERO;
                for (int i = 0; i < splits.size(); i++) {
                    SplitRequest in = splits.get(i);
                    SplitRequest out = new SplitRequest();
                    out.setUserId(in.getUserId());
                    BigDecimal pct = in.getRawValue() == null ? BigDecimal.ZERO : in.getRawValue();
                    BigDecimal owed = (i == splits.size() - 1)
                            ? amount.subtract(running)
                            : amount.multiply(pct).divide(new BigDecimal("100"), MONEY_SCALE, MONEY_ROUNDING);
                    out.setAmountOwed(owed.setScale(MONEY_SCALE, MONEY_ROUNDING));
                    out.setRawValue(pct);
                    running = running.add(owed);
                    result.add(out);
                }
            }
            case SHARES -> {
                BigDecimal totalShares = splits.stream()
                        .map(s -> s.getRawValue() == null ? BigDecimal.ZERO : s.getRawValue())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (totalShares.signum() <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total shares must be > 0");
                }
                BigDecimal running = BigDecimal.ZERO;
                for (int i = 0; i < splits.size(); i++) {
                    SplitRequest in = splits.get(i);
                    SplitRequest out = new SplitRequest();
                    out.setUserId(in.getUserId());
                    BigDecimal shares = in.getRawValue() == null ? BigDecimal.ZERO : in.getRawValue();
                    BigDecimal owed = (i == splits.size() - 1)
                            ? amount.subtract(running)
                            : amount.multiply(shares).divide(totalShares, MONEY_SCALE, MONEY_ROUNDING);
                    out.setAmountOwed(owed.setScale(MONEY_SCALE, MONEY_ROUNDING));
                    out.setRawValue(shares);
                    running = running.add(owed);
                    result.add(out);
                }
            }
            case EXACT -> {
                BigDecimal sum = BigDecimal.ZERO;
                for (SplitRequest s : splits) {
                    if (s.getAmountOwed() == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "EXACT splits require amountOwed for every entry");
                    }
                    sum = sum.add(s.getAmountOwed());
                }
                if (sum.subtract(amount).abs().compareTo(SUM_TOLERANCE) > 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Sum of split amounts (" + sum + ") does not equal total expense amount (" + amount + ")");
                }
                for (SplitRequest in : splits) {
                    SplitRequest out = new SplitRequest();
                    out.setUserId(in.getUserId());
                    out.setAmountOwed(in.getAmountOwed().setScale(MONEY_SCALE, MONEY_ROUNDING));
                    out.setRawValue(in.getRawValue());
                    result.add(out);
                }
            }
        }
        return result;
    }
}
