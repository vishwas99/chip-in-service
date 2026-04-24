package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dto.AddMemberRequest;
import com.chipIn.ChipIn.dto.GroupDashboardResponse;
import com.chipIn.ChipIn.dto.GroupResponse;
import com.chipIn.ChipIn.dto.GroupsTabResponse;
import com.chipIn.ChipIn.entities.*;
import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupCurrencyRepository groupCurrencyRepository;
    private final CurrencyRepository currencyRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseService expenseService;

    @Transactional // Ensures Group, Member, and Currency all save together
    public Group createGroup(Group groupData, User creator) {

        // 1. Validate & Set the Master Currency FIRST
        Currency masterCurrency = currencyRepository.findById(groupData.getDefaultCurrency().getCurrencyId())
                .orElseThrow(() -> new RuntimeException("Master Currency not found"));
                
        // 🚨 Verify if the currency is valid (active and is a global currency)
        if (!masterCurrency.isActive() || masterCurrency.getGroup() != null) {
            throw new IllegalArgumentException("Invalid currency. Only active global currencies can be set as default for a new group.");
        }
        
        groupData.setDefaultCurrency(masterCurrency);
        groupData.setCreatedBy(creator.getUserid());

        // 2. Save Group
        Group savedGroup = groupRepository.save(groupData);

        // 3. Create Admin Membership
        GroupMemberId membershipId = new GroupMemberId(savedGroup.getGroupId(), creator.getUserid());
        GroupMember creatorMembership = GroupMember.builder()
                .id(membershipId)
                .group(savedGroup)
                .user(creator)
                .isAdmin(true)
                .status("ACTIVE")
                .build();
        groupMemberRepository.save(creatorMembership);

        // 4. ✅ CRITICAL STEP: Create the Default Group Currency (The "Normal" Currency)
        GroupCurrency defaultGroupCurrency = GroupCurrency.builder()
                .group(savedGroup)
                .masterCurrency(masterCurrency) // Link to INR/USD
                .name("Base " + masterCurrency.getCode()) // e.g., "Base INR"
                .exchangeRate(BigDecimal.ONE) // Normal currency always has rate 1.0
                .createdBy(creator)
                .build();
        groupCurrencyRepository.save(defaultGroupCurrency);

        return savedGroup;
    }

    @Transactional
    public void addMemberToGroup(UUID groupId, AddMemberRequest request, User currentUser) {

        // 1. Fetch the Group
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        // 2. Permission Check: Is the current user an Admin of this group?
        GroupMemberId currentUserMemberId = new GroupMemberId(groupId, currentUser.getUserid());
        GroupMember currentMember = groupMemberRepository.findById(currentUserMemberId)
                .orElseThrow(() -> new RuntimeException("You are not a member of this group"));

        if (!currentMember.isAdmin()) {
            throw new RuntimeException("Only group admins can add new members");
        }

        // 3. Fetch the New User by Email
        User newUser = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User with email " + request.getEmail() + " not found"));

        // 4. Duplicate Check: Is the new user already in the group?
        GroupMemberId newMemberId = new GroupMemberId(groupId, newUser.getUserid());
        if (groupMemberRepository.existsById(newMemberId)) {
            throw new RuntimeException("User is already a member of this group");
        }

        // 5. Create the new Membership
        GroupMember newMembership = GroupMember.builder()
                .id(newMemberId)
                .group(group)
                .user(newUser)
                .isAdmin(request.isAdmin())
                .status("ACTIVE")
                .build();

        // 6. Save
        groupMemberRepository.save(newMembership);
    }
    
    @Transactional
    public GroupCurrency addGroupCurrency(UUID groupId, UUID currencyId, String name, BigDecimal exchangeRate, User currentUser) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
                
        Currency currency = currencyRepository.findById(currencyId)
                .orElseThrow(() -> new RuntimeException("Currency not found"));
                
        // 🚨 Verify if the currency is valid for this group (global or specific to this group)
        if (!currency.isActive()) {
            throw new IllegalArgumentException("Currency is not active.");
        }
        if (currency.getGroup() != null && !currency.getGroup().getGroupId().equals(groupId)) {
            throw new IllegalArgumentException("This custom currency does not belong to this group.");
        }
        
        GroupCurrency groupCurrency = GroupCurrency.builder()
                .group(group)
                .masterCurrency(currency)
                .name(name)
                .exchangeRate(exchangeRate)
                .createdBy(currentUser)
                .build();
                
        return groupCurrencyRepository.save(groupCurrency);
    }

    public GroupDashboardResponse getGroupDashboard(UUID groupId, UUID currentUserId) {
        // 1. Fetch Group and Expenses
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        List<Expense> expenses = expenseRepository.findByGroupGroupIdAndIsDeletedFalseOrderByCreatedAtDesc(groupId);

        // 2. Prepare Data Structures
        Map<UUID, BigDecimal> totalBalances = new HashMap<>(); // UserID -> Total Amount
        List<GroupDashboardResponse.ExpenseSummaryDto> expenseSummaries = new ArrayList<>();

        // 3. THE LOOP: Process every expense
        for (Expense expense : expenses) {
            BigDecimal rate = expense.getCurrency().getExchangeRate();
            BigDecimal myShareInThisExpense = BigDecimal.ZERO;

            // A. Process Payers (+)
            for (ExpensePayer payer : expense.getPayers()) {
                BigDecimal amountInDefault = payer.getPaidAmount().multiply(rate);

                // Add to Global Group Balance
                totalBalances.merge(payer.getUser().getUserid(), amountInDefault, BigDecimal::add);

                // Calculate "My Share" for the list view
                if (payer.getUser().getUserid().equals(currentUserId)) {
                    myShareInThisExpense = myShareInThisExpense.add(amountInDefault);
                }
            }

            // B. Process Splits (-)
            for (ExpenseSplit split : expense.getSplits()) {
                BigDecimal amountInDefault = split.getAmountOwed().multiply(rate);

                // Subtract from Global Group Balance
                totalBalances.merge(split.getUser().getUserid(), amountInDefault.negate(), BigDecimal::add);

                // Calculate "My Share" for the list view
                if (split.getUser().getUserid().equals(currentUserId)) {
                    myShareInThisExpense = myShareInThisExpense.subtract(amountInDefault);
                }
            }

            // C. Create the Expense Summary DTO
            expenseSummaries.add(GroupDashboardResponse.ExpenseSummaryDto.builder()
                    .expenseId(expense.getExpenseId())
                    .description(expense.getDescription())
                    .date(expense.getCreatedAt())
                    .category(expense.getCategory())
                    .type(expense.getType().toString())
                    .yourNetShare(myShareInThisExpense) // + means you lent, - means you owe
                    .formattedShare(myShareInThisExpense.compareTo(BigDecimal.ZERO) >= 0 ? "You lent" : "You owe")
                    .build());
        }

        // 4. Convert Balance Map to List DTO
        List<GroupDashboardResponse.UserBalanceDto> userBalanceDtos = totalBalances.entrySet().stream()
                .map(entry -> {
                    User user = userRepository.findById(entry.getKey()).orElse(null); // Simple lookup
                    return GroupDashboardResponse.UserBalanceDto.builder()
                            .userId(entry.getKey())
                            .userName(user != null ? user.getUsername() : "Unknown")
                            .netBalance(entry.getValue())
                            .build();
                }).toList();

        // 4. Calculate Settlements
        List<GroupDashboardResponse.SettlementSuggestionDto> settlements = calculateSettlements(totalBalances);

        // 5. Enrich with Names (Since DTO only has IDs currently)
        settlements.forEach(s -> {
            s.setPayerName(userRepository.findById(s.getPayerId()).get().getUsername());
            s.setPayeeName(userRepository.findById(s.getPayeeId()).get().getUsername());
        });

        // 5. Final Return
        return GroupDashboardResponse.builder()
                .groupId(group.getGroupId())
                .groupName(group.getName())
                .targetCurrencyId(group.getDefaultCurrency().getCurrencyId())
                .currencyCode(group.getDefaultCurrency().getCode())
                .userBalances(userBalanceDtos)
                .expenses(expenseSummaries)
                .settlements(settlements) // <--- Added!
                .build();
    }

    private List<GroupDashboardResponse.SettlementSuggestionDto> calculateSettlements(Map<UUID, BigDecimal> balances) {
        List<GroupDashboardResponse.SettlementSuggestionDto> suggestions = new ArrayList<>();

        // 1. Separate into Debtors (-) and Creditors (+)
        // We use a helper class or simple AbstractMap.SimpleEntry for sorting
        List<Map.Entry<UUID, BigDecimal>> debtors = new ArrayList<>();
        List<Map.Entry<UUID, BigDecimal>> creditors = new ArrayList<>();

        for (Map.Entry<UUID, BigDecimal> entry : balances.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(entry);
            } else if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(entry);
            }
        }

        // 2. Sort by magnitude (Largest amounts first)
        debtors.sort(Comparator.comparing(Map.Entry::getValue)); // Ascending (e.g. -1000 before -500)
        creditors.sort((a, b) -> b.getValue().compareTo(a.getValue())); // Descending (e.g. +1000 before +500)

        // 3. The Greedy Matcher
        int i = 0; // Debtor pointer
        int j = 0; // Creditor pointer

        while (i < debtors.size() && j < creditors.size()) {
            Map.Entry<UUID, BigDecimal> debtor = debtors.get(i);
            Map.Entry<UUID, BigDecimal> creditor = creditors.get(j);

            BigDecimal debtAmount = debtor.getValue().abs();
            BigDecimal creditAmount = creditor.getValue();

            BigDecimal settlementAmount = debtAmount.min(creditAmount);

            // A. Add Suggestion
            // Note: In real app, fetch User Names. Here we use IDs for the logic.
            suggestions.add(GroupDashboardResponse.SettlementSuggestionDto.builder()
                    .payerId(debtor.getKey())
                    .payeeId(creditor.getKey())
                    .amount(settlementAmount)
                    .build());

            // B. Update Remaining Amounts
            debtor.setValue(debtor.getValue().add(settlementAmount)); // -100 + 50 = -50
            creditor.setValue(creditor.getValue().subtract(settlementAmount)); // +100 - 50 = +50

            // C. Move Pointers
            if (debtor.getValue().compareTo(BigDecimal.ZERO) == 0) i++;
            if (creditor.getValue().compareTo(BigDecimal.ZERO) == 0) j++;
        }

        return suggestions;
    }

    public GroupsTabResponse getGroupsDataByUserId(UUID userId){
//        1. Get all groups user belongs to
//        2. For each group get all expenses
        List<Expense> userExpense = expenseService.getExpensesForUser(userId);
        log.info(userExpense.toString());
//        3. For each Expense get all splits
        List<ExpenseSplit> userSplits = expenseService.getSplitsByExpenses(userExpense);
        log.info(userSplits.toString());

//        4. Tally Expenses - If Paid by user subtract paid splits
        return null;
    }

    public Object groupAndTallySplitsForUser(List<Expense> expenses, UUID userId){
        /*



         */
        for(Expense expense : expenses){

            BigDecimal amountPaidByUser = BigDecimal.ZERO;

            for(ExpensePayer payer : expense.getPayers()){
                if(payer.getUser().getUserid().equals(userId)){
                    amountPaidByUser =  amountPaidByUser.add(payer.getPaidAmount());
                }

            }

        }

        return null;
    }

    public List<GroupResponse> getUserGroups(UUID userId) {
        List<GroupMember> memberships = groupMemberRepository.findByIdUserId(userId);
        
        return memberships.stream()
                .filter(m -> !m.getGroup().isDeleted())
                .map(m -> {
                    Group group = m.getGroup();
                    return GroupResponse.builder()
                            .groupId(group.getGroupId())
                            .name(group.getName())
                            .description(group.getDescription())
                            .imageUrl(group.getImageUrl())
                            .type(group.getType())
                            .simplifyDebt(group.isSimplifyDebt())
                            .defaultCurrency(group.getDefaultCurrency())
                            .createdAt(group.getCreatedAt())
                            .isAdmin(m.isAdmin())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
