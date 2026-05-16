package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dto.CreateSettlementRequest;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ExpensePayerRepository expensePayerRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final CurrencyService currencyService;
    private final AccessGuard accessGuard;

    @Transactional
    public Expense createSettlement(CreateSettlementRequest request, User currentUser) {
        if (request.getPayerId().equals(request.getPayeeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payer and payee must be different users");
        }

        Group group = accessGuard.requireGroup(request.getGroupId());
        GroupMember actor = accessGuard.requireGroupMember(group, currentUser);

        boolean actorIsPayer = currentUser.getUserid().equals(request.getPayerId());
        if (!actorIsPayer && !actor.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the payer (or a group admin) can record a settlement");
        }

        if (!accessGuard.isMember(group.getGroupId(), request.getPayerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payer is not a member of this group");
        }
        if (!accessGuard.isMember(group.getGroupId(), request.getPayeeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payee is not a member of this group");
        }

        User payer = userRepository.findById(request.getPayerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payer not found"));
        User payee = userRepository.findById(request.getPayeeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payee not found"));

        GroupCurrency currency = currencyService.validateAndGetGroupCurrency(
                request.getGroupId(), request.getCurrencyId(), currentUser);

        String description = (request.getNotes() != null && !request.getNotes().isBlank())
                ? request.getNotes()
                : payer.getName() + " paid " + payee.getName();

        Expense settlement = Expense.builder()
                .group(group)
                .createdBy(currentUser)
                .description(description)
                .amount(request.getAmount())
                .currency(currency)
                .type(ExpenseType.SETTLEMENT)
                .splitType(SplitType.EXACT)
                .build();

        Expense saved = expenseRepository.save(settlement);

        expensePayerRepository.save(ExpensePayer.builder()
                .expense(saved)
                .user(payer)
                .paidAmount(request.getAmount())
                .build());

        expenseSplitRepository.save(ExpenseSplit.builder()
                .expense(saved)
                .user(payee)
                .amountOwed(request.getAmount())
                .build());

        log.info("Settlement {} recorded in group {} (payer={}, payee={}, amount={})",
                saved.getExpenseId(), group.getGroupId(), payer.getUserid(), payee.getUserid(),
                request.getAmount());

        return saved;
    }
}
