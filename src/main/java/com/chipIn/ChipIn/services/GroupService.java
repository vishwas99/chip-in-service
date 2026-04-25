package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dto.*;
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

            // C. Create the Expense Summary DTO (exclude SETTLEMENT type - they appear in settlements array)
            if (!expense.getType().toString().equals("SETTLEMENT")) {
                expenseSummaries.add(GroupDashboardResponse.ExpenseSummaryDto.builder()
                        .expenseId(expense.getExpenseId())
                        .description(expense.getDescription())
                        .date(expense.getCreatedAt())
                        .category(expense.getCategory())
                        .type(expense.getType().toString())
                        .createdByName(expense.getCreatedBy().getName()) // Add the creator's name
                        .yourNetShare(myShareInThisExpense) // + means you lent, - means you owe
                        .formattedShare(myShareInThisExpense.compareTo(BigDecimal.ZERO) >= 0 ? "You lent" : "You owe")
                        .build());
            }
        }

        // 4. Convert Balance Map to List DTO (excluding deleted users)
        List<GroupDashboardResponse.UserBalanceDto> userBalanceDtos = totalBalances.entrySet().stream()
                .map(entry -> {
                    User user = userRepository.findById(entry.getKey()).orElse(null);
                    return GroupDashboardResponse.UserBalanceDto.builder()
                            .userId(entry.getKey())
                            .userName(user != null ? user.getUsername() : "Unknown")
                            .netBalance(entry.getValue())
                            .build();
                }).toList();

        // 4. Calculate Settlements
        List<GroupDashboardResponse.SettlementSuggestionDto> settlements = calculateSettlements(totalBalances);

        // 5. Enrich with Names (Since DTO only has IDs currently) - only include active users
        settlements.forEach(s -> {
            Optional<User> payer = userRepository.findById(s.getPayerId());
            Optional<User> payee = userRepository.findById(s.getPayeeId());
            if (payer.isPresent() && payee.isPresent()) {
                s.setPayerName(payer.get().getUsername());
                s.setPayeeName(payee.get().getUsername());
            }
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

    public Boolean isUserInGroup(UUID groupId, UUID userId){
        return groupMemberRepository.findByGroupIdAndUserId(groupId, userId) != null;
    }

    public List<FriendResponse> getGroupUsers(UUID groupId){
        List<GroupMember> members = groupMemberRepository.findByGroupGroupId(groupId);
        return members.stream().map(m ->{
            return new FriendResponse(m.getUser().getUserid(), m.getUser().getName(), m.getUser().getEmail(), m.getUser().getProfilePicUrl());
        }).toList();
    }

    public Currency getGeoBasedCurrency(){
        return currencyRepository.findByCode("INR").orElseThrow(() -> new RuntimeException("Default currency INR not found"));
    }

    @Transactional
    public void deleteGroup(UUID groupId, boolean hardDelete, User currentUser) {
        // 1. Fetch the Group
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        // 2. Permission Check: Is the current user an Admin of this group?
        GroupMemberId currentUserMemberId = new GroupMemberId(groupId, currentUser.getUserid());
        GroupMember currentMember = groupMemberRepository.findById(currentUserMemberId)
                .orElseThrow(() -> new RuntimeException("You are not a member of this group"));

        if (!currentMember.isAdmin()) {
            throw new RuntimeException("Only group admins can delete the group");
        }

        if (hardDelete) {
            // Hard delete: Mark all expenses as deleted
            List<Expense> expenses = expenseRepository.findByGroup_GroupId(groupId);
            for (Expense expense : expenses) {
                expense.setDeleted(true);
                expenseRepository.save(expense);
            }
            // Mark group as deleted
            group.setDeleted(true);
            groupRepository.save(group);
        } else {
            // Soft delete: Check for unsettled expenses
            Map<UUID, BigDecimal> totalBalances = calculateGroupBalances(groupId);
            boolean hasUnsettled = totalBalances.values().stream().anyMatch(balance -> balance.compareTo(BigDecimal.ZERO) != 0);

            if (hasUnsettled) {
                // Mark group as deleted
                group.setDeleted(true);
                groupRepository.save(group);
            } else {
                throw new RuntimeException("Cannot delete group: There are unsettled expenses. Use hard delete to force deletion.");
            }
        }
    }

    private Map<UUID, BigDecimal> calculateGroupBalances(UUID groupId) {
        List<Expense> expenses = expenseRepository.findByGroupGroupIdAndIsDeletedFalse(groupId);
        Map<UUID, BigDecimal> totalBalances = new HashMap<>();

        for (Expense expense : expenses) {
            BigDecimal rate = expense.getCurrency().getExchangeRate();

            // Process Payers (+)
            for (ExpensePayer payer : expense.getPayers()) {
                BigDecimal amountInDefault = payer.getPaidAmount().multiply(rate);
                totalBalances.merge(payer.getUser().getUserid(), amountInDefault, BigDecimal::add);
            }

            // Process Splits (-)
            for (ExpenseSplit split : expense.getSplits()) {
                BigDecimal amountInDefault = split.getAmountOwed().multiply(rate);
                totalBalances.merge(split.getUser().getUserid(), amountInDefault.negate(), BigDecimal::add);
            }
        }

        return totalBalances;
    }

    public GroupBalancesResponse getGroupBalances(UUID groupId, UUID currentUserId) {
        // 1. Fetch the Group
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        // 2. Permission Check: Is the current user a member of this group?
        GroupMemberId currentUserMemberId = new GroupMemberId(groupId, currentUserId);
        GroupMember currentMember = groupMemberRepository.findById(currentUserMemberId)
                .orElseThrow(() -> new RuntimeException("You are not a member of this group"));

        // 3. Get all non-deleted expenses in the group (including settlements)
        List<Expense> expenses = expenseRepository.findByGroupGroupIdAndIsDeletedFalse(groupId);

        // 4. Calculate pairwise balances and transactions
        Map<String, BigDecimal> userPairBalances = new HashMap<>(); // "user1_user2" -> amount (user1 owes user2)
        Map<String, List<GroupBalancesResponse.TransactionDto>> userPairTransactions = new HashMap<>();

        for (Expense expense : expenses) {
            BigDecimal rate = expense.getCurrency().getExchangeRate();
            BigDecimal totalAmount = expense.getAmount().multiply(rate);

            // Calculate total paid and total owed
            BigDecimal totalPaid = expense.getPayers().stream()
                    .map(p -> p.getPaidAmount().multiply(rate))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalOwed = expense.getSplits().stream()
                    .map(s -> s.getAmountOwed().multiply(rate))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // For each payer-splitter pair
            for (ExpensePayer payer : expense.getPayers()) {
                for (ExpenseSplit splitter : expense.getSplits()) {
                    if (!payer.getUser().getUserid().equals(splitter.getUser().getUserid())) {
                        // Calculate how much this payer owes this splitter for this expense
                        BigDecimal payerContribution = payer.getPaidAmount().multiply(rate);
                        BigDecimal splitterShare = splitter.getAmountOwed().multiply(rate);

                        // Proportional amount: (payer's payment / total paid) * splitter's share
                        BigDecimal amountOwed = BigDecimal.ZERO;
                        if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
                            amountOwed = payerContribution.multiply(splitterShare).divide(totalPaid, 2, BigDecimal.ROUND_HALF_UP);
                        }

                        // Create canonical pair key (smaller UUID first)
                        UUID payerId = payer.getUser().getUserid();
                        UUID splitterId = splitter.getUser().getUserid();
                        String pairKey = createPairKey(payerId, splitterId);

                        // Update balance: store always as (firstUuid owes secondUuid).
                        // amountOwed is amount payer owes splitter. If payerId is the first UUID in the
                        // canonical ordering, add as positive; otherwise store as negative so the
                        // canonical meaning (first owes second) holds.
                        BigDecimal currentBalance = userPairBalances.getOrDefault(pairKey, BigDecimal.ZERO);
                        if (payerId.compareTo(splitterId) < 0) {
                            userPairBalances.put(pairKey, currentBalance.add(amountOwed));
                        } else {
                            userPairBalances.put(pairKey, currentBalance.add(amountOwed.negate()));
                        }

                        // Add transaction for payer (negative = paid)
                        String payerKey = payer.getUser().getUserid() + "_" + splitter.getUser().getUserid();
                        userPairTransactions.computeIfAbsent(payerKey, k -> new ArrayList<>()).add(
                                GroupBalancesResponse.TransactionDto.builder()
                                        .transactionId(expense.getExpenseId())
                                        .type(expense.getType().toString())
                                        .description(expense.getDescription())
                                        .date(expense.getCreatedAt())
                                        .amount(amountOwed.negate()) // Negative because payer paid
                                        .currencyCode(expense.getCurrency().getMasterCurrency().getCode())
                                        .build()
                        );

                        // Add transaction for splitter (positive = received)
                        String splitterKey = splitter.getUser().getUserid() + "_" + payer.getUser().getUserid();
                        userPairTransactions.computeIfAbsent(splitterKey, k -> new ArrayList<>()).add(
                                GroupBalancesResponse.TransactionDto.builder()
                                        .transactionId(expense.getExpenseId())
                                        .type(expense.getType().toString())
                                        .description(expense.getDescription())
                                        .date(expense.getCreatedAt())
                                        .amount(amountOwed) // Positive because splitter received
                                        .currencyCode(expense.getCurrency().getMasterCurrency().getCode())
                                        .build()
                        );
                    }
                }
            }
        }

        // 5. Calculate net balance per user from current user's perspective
        Map<UUID, BigDecimal> userNetBalances = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : userPairBalances.entrySet()) {
            String[] userIds = entry.getKey().split("_");
            UUID user1 = UUID.fromString(userIds[0]);
            UUID user2 = UUID.fromString(userIds[1]);
            BigDecimal amount = entry.getValue();

            // user1 owes user2 the amount
            // From user1's perspective: they owe user2 (negative)
            // From user2's perspective: user1 owes them (positive)
            userNetBalances.merge(user1, amount.negate(), BigDecimal::add); // user1 owes
            userNetBalances.merge(user2, amount, BigDecimal::add); // user2 is owed
        }

        // 6. Create user balance DTOs (excluding current user)
        List<GroupBalancesResponse.UserBalanceDto> userBalanceDtos = userNetBalances.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(currentUserId))
                .map(entry -> {
                    User user = userRepository.findByIdActive(entry.getKey()).orElse(null);
                    String balanceStatus;
                    if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                        balanceStatus = "Owes you";
                    } else if (entry.getValue().compareTo(BigDecimal.ZERO) < 0) {
                        balanceStatus = "You owe";
                    } else {
                        balanceStatus = "Settled";
                    }

                    return GroupBalancesResponse.UserBalanceDto.builder()
                            .userId(entry.getKey())
                            .userName(user != null ? user.getName() : "Unknown")
                            .netBalance(entry.getValue())
                            .balanceStatus(balanceStatus)
                            .build();
                })
                .sorted((a, b) -> b.getNetBalance().abs().compareTo(a.getNetBalance().abs()))
                .collect(Collectors.toList());

        // 7. Create transaction history per user
        List<GroupBalancesResponse.UserTransactionHistoryDto> transactionHistory = userNetBalances.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(currentUserId))
                .map(entry -> {
                    User otherUser = userRepository.findByIdActive(entry.getKey()).orElse(null);
                    // Fetch transactions where current user is payer and where current user is splitter
                    List<GroupBalancesResponse.TransactionDto> txFromCurrent = userPairTransactions.getOrDefault(
                            currentUserId + "_" + entry.getKey(), new ArrayList<>());
                    List<GroupBalancesResponse.TransactionDto> txFromOther = userPairTransactions.getOrDefault(
                            entry.getKey() + "_" + currentUserId, new ArrayList<>());

                    // Combine and deduplicate transactions by transactionId, preferring the entry
                    // that represents the perspective of the current user (payer -> negative amount,
                    // receiver -> positive amount). This avoids showing the same expense twice with
                    // both signs.
                    List<GroupBalancesResponse.TransactionDto> combined = new ArrayList<>();
                    combined.addAll(txFromCurrent);
                    combined.addAll(txFromOther);

                    Map<java.util.UUID, java.util.List<GroupBalancesResponse.TransactionDto>> grouped = combined.stream()
                            .collect(Collectors.groupingBy(GroupBalancesResponse.TransactionDto::getTransactionId, LinkedHashMap::new, Collectors.toList()));

                    List<GroupBalancesResponse.TransactionDto> transactions = new ArrayList<>();
                    for (Map.Entry<java.util.UUID, java.util.List<GroupBalancesResponse.TransactionDto>> gentry : grouped.entrySet()) {
                        java.util.UUID txId = gentry.getKey();
                        java.util.List<GroupBalancesResponse.TransactionDto> variants = gentry.getValue();

                        // Determine if current user acted as payer in the original expense
                        Expense relatedExpense = expenseRepository.findById(txId).orElse(null);
                        boolean currentIsPayer = false;
                        if (relatedExpense != null) {
                            currentIsPayer = relatedExpense.getPayers().stream()
                                    .anyMatch(p -> p.getUser().getUserid().equals(currentUserId));
                        }

                        GroupBalancesResponse.TransactionDto chosen = null;
                        if (currentIsPayer) {
                            // prefer negative amount (current user paid)
                            for (GroupBalancesResponse.TransactionDto v : variants) {
                                if (v.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                                    chosen = v;
                                    break;
                                }
                            }
                        } else {
                            // prefer positive amount (current user received)
                            for (GroupBalancesResponse.TransactionDto v : variants) {
                                if (v.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                                    chosen = v;
                                    break;
                                }
                            }
                        }

                        if (chosen == null && !variants.isEmpty()) chosen = variants.get(0);
                        if (chosen != null) transactions.add(chosen);
                    }

                    // Sort transactions by date (newest first)
                    transactions.sort((a, b) -> b.getDate().compareTo(a.getDate()));

                    return GroupBalancesResponse.UserTransactionHistoryDto.builder()
                            .otherUserId(entry.getKey())
                            .otherUserName(otherUser != null ? otherUser.getName() : "Unknown")
                            .netAmount(entry.getValue())
                            .transactions(transactions)
                            .build();
                })
                .sorted((a, b) -> b.getNetAmount().abs().compareTo(a.getNetAmount().abs()))
                .collect(Collectors.toList());

        return GroupBalancesResponse.builder()
                .groupId(group.getGroupId())
                .groupName(group.getName())
                .currencyCode(group.getDefaultCurrency().getCode())
                .userBalances(userBalanceDtos)
                .transactionHistory(transactionHistory)
                .build();
    }

    private String createPairKey(UUID user1, UUID user2) {
        return user1.compareTo(user2) < 0 ? user1 + "_" + user2 : user2 + "_" + user1;
    }
}
