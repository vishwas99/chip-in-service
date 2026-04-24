package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dto.HomeFriendsResponse;
import com.chipIn.ChipIn.dto.HomeGroupsResponse;
import com.chipIn.ChipIn.entities.*;
import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import com.chipIn.ChipIn.repository.GroupMemberRepository;
import com.chipIn.ChipIn.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseService expenseService;
    private final CurrencyRepository currencyRepository;
    private final UserRepository userRepository;

    public HomeGroupsResponse getHomeGroupsData(UUID userId, UUID displayCurrencyId) {
        // 1. Resolve Display Currency
        Currency displayCurrency = currencyRepository.findById(displayCurrencyId)
                .orElseThrow(() -> new RuntimeException("Display Currency not found"));

        // 2. Fetch User's Groups
        List<GroupMember> memberships = groupMemberRepository.findByIdUserId(userId);
        
        BigDecimal totalOwedToYou = BigDecimal.ZERO;
        BigDecimal totalYouOwe = BigDecimal.ZERO;
        List<HomeGroupsResponse.GroupSummaryDto> groupSummaries = new ArrayList<>();

        // 3. Process Each Group
        for (GroupMember membership : memberships) {
            Group group = membership.getGroup();
            
            // Get expenses specifically for this group (could optimize with a specialized query)
            List<Expense> groupExpenses = expenseService.getExpensesForUser(userId).stream()
                    .filter(e -> e.getGroup().getGroupId().equals(group.getGroupId()))
                    .toList();

            BigDecimal netGroupBalanceInMasterCurrency = BigDecimal.ZERO;
            LocalDateTime lastActivity = group.getCreatedAt();

            // Calculate balance for this specific group
            for (Expense expense : groupExpenses) {
                if (expense.getCreatedAt().isAfter(lastActivity)) {
                    lastActivity = expense.getCreatedAt();
                }

                // Get the rate to convert the custom currency back to the GROUP's master currency
                BigDecimal toMasterRate = expense.getCurrency().getExchangeRate();
                BigDecimal expenseBalanceInMaster = BigDecimal.ZERO;

                // What I paid (+)
                for (ExpensePayer payer : expense.getPayers()) {
                    if (payer.getUser().getUserid().equals(userId)) {
                        expenseBalanceInMaster = expenseBalanceInMaster.add(payer.getPaidAmount().multiply(toMasterRate));
                    }
                }

                // What I owe (-)
                for (ExpenseSplit split : expense.getSplits()) {
                    if (split.getUser().getUserid().equals(userId)) {
                        expenseBalanceInMaster = expenseBalanceInMaster.subtract(split.getAmountOwed().multiply(toMasterRate));
                    }
                }
                
                netGroupBalanceInMasterCurrency = netGroupBalanceInMasterCurrency.add(expenseBalanceInMaster);
            }

            // Cross-currency conversion (Group's Master -> Requested Display Currency)
            // Note: Realistically, you need a live exchange rate API here. 
            // For now, we assume 1:1 if currencies match, else we throw an error or mock it.
            BigDecimal netBalanceInDisplayCurrency;
            if (group.getDefaultCurrency().getCurrencyId().equals(displayCurrencyId)) {
                 netBalanceInDisplayCurrency = netGroupBalanceInMasterCurrency;
            } else {
                 // TODO: Implement cross-currency conversion logic (e.g. INR -> USD)
                 // For MVP, we will just pass it through or you could mock a fixed rate table.
                 // This requires a GlobalExchangeRate table or external API call.
                 netBalanceInDisplayCurrency = netGroupBalanceInMasterCurrency; // Temporary mock
            }

            // Aggregate totals
            if (netBalanceInDisplayCurrency.compareTo(BigDecimal.ZERO) > 0) {
                totalOwedToYou = totalOwedToYou.add(netBalanceInDisplayCurrency);
            } else {
                totalYouOwe = totalYouOwe.add(netBalanceInDisplayCurrency.abs());
            }

            groupSummaries.add(HomeGroupsResponse.GroupSummaryDto.builder()
                    .groupId(group.getGroupId())
                    .groupName(group.getName())
                    .groupImageUrl(group.getImageUrl())
                    .netBalance(netBalanceInDisplayCurrency)
                    .lastActivity(lastActivity)
                    .build());
        }

        // Sort by last activity descending
        groupSummaries.sort((a, b) -> b.getLastActivity().compareTo(a.getLastActivity()));

        return HomeGroupsResponse.builder()
                .totalOwedToYou(totalOwedToYou)
                .totalYouOwe(totalYouOwe)
                .displayCurrencyId(displayCurrencyId)
                .displayCurrencyCode(displayCurrency.getCode())
                .groups(groupSummaries)
                .build();
    }

    public HomeFriendsResponse getHomeFriendsData(UUID userId, UUID displayCurrencyId) {
        Currency displayCurrency = currencyRepository.findById(displayCurrencyId)
                .orElseThrow(() -> new RuntimeException("Display Currency not found"));

        // Get all expenses involving this user across all groups
        List<Expense> allExpenses = expenseService.getExpensesForUser(userId);

        // Map to store Net Balance against every specific Friend (FriendID -> Net Balance in Display Currency)
        Map<UUID, BigDecimal> friendBalances = new HashMap<>();

        for (Expense expense : allExpenses) {
            BigDecimal toMasterRate = expense.getCurrency().getExchangeRate();
            
            // To figure out who owes who for a specific expense, we need the simplified settlement logic
            // for just this one expense.
            
            // 1. Tally balances for this specific expense
            Map<UUID, BigDecimal> expenseBalances = new HashMap<>();
            
            for (ExpensePayer payer : expense.getPayers()) {
                BigDecimal amountInMaster = payer.getPaidAmount().multiply(toMasterRate);
                expenseBalances.merge(payer.getUser().getUserid(), amountInMaster, BigDecimal::add);
            }
            
            for (ExpenseSplit split : expense.getSplits()) {
                BigDecimal amountInMaster = split.getAmountOwed().multiply(toMasterRate);
                expenseBalances.merge(split.getUser().getUserid(), amountInMaster.negate(), BigDecimal::add);
            }
            
            // 2. Calculate simplified settlements for THIS expense to see who owes me / who I owe
            // Using a simplified greedy approach similar to group dashboard
            List<Map.Entry<UUID, BigDecimal>> debtors = new ArrayList<>();
            List<Map.Entry<UUID, BigDecimal>> creditors = new ArrayList<>();

            for (Map.Entry<UUID, BigDecimal> entry : expenseBalances.entrySet()) {
                if (entry.getValue().compareTo(BigDecimal.ZERO) < 0) debtors.add(entry);
                else if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) creditors.add(entry);
            }

            debtors.sort(Comparator.comparing(Map.Entry::getValue));
            creditors.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            int i = 0, j = 0;
            while (i < debtors.size() && j < creditors.size()) {
                Map.Entry<UUID, BigDecimal> debtor = debtors.get(i);
                Map.Entry<UUID, BigDecimal> creditor = creditors.get(j);

                BigDecimal debtAmount = debtor.getValue().abs();
                BigDecimal creditAmount = creditor.getValue();
                BigDecimal settlementAmount = debtAmount.min(creditAmount);
                
                // Cross currency handling (Mocked for MVP)
                BigDecimal finalAmountInDisplay = expense.getGroup().getDefaultCurrency().getCurrencyId().equals(displayCurrencyId) 
                        ? settlementAmount 
                        : settlementAmount; // TODO: Cross currency conversion

                // If I am the Debtor (I owe the Creditor)
                if (debtor.getKey().equals(userId)) {
                    friendBalances.merge(creditor.getKey(), finalAmountInDisplay.negate(), BigDecimal::add);
                } 
                // If I am the Creditor (The Debtor owes me)
                else if (creditor.getKey().equals(userId)) {
                    friendBalances.merge(debtor.getKey(), finalAmountInDisplay, BigDecimal::add);
                }

                debtor.setValue(debtor.getValue().add(settlementAmount));
                creditor.setValue(creditor.getValue().subtract(settlementAmount));

                if (debtor.getValue().compareTo(BigDecimal.ZERO) == 0) i++;
                if (creditor.getValue().compareTo(BigDecimal.ZERO) == 0) j++;
            }
        }

        BigDecimal totalOwedToYou = BigDecimal.ZERO;
        BigDecimal totalYouOwe = BigDecimal.ZERO;
        List<HomeFriendsResponse.FriendSummaryDto> friendSummaries = new ArrayList<>();

        for (Map.Entry<UUID, BigDecimal> entry : friendBalances.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) == 0) continue; // Skip settled friends

            User friend = userRepository.findById(entry.getKey()).orElse(null);
            if (friend == null) continue;

            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                totalOwedToYou = totalOwedToYou.add(entry.getValue());
            } else {
                totalYouOwe = totalYouOwe.add(entry.getValue().abs());
            }

            friendSummaries.add(HomeFriendsResponse.FriendSummaryDto.builder()
                    .friendId(friend.getUserid())
                    .friendName(friend.getUsername())
                    .friendProfilePic(friend.getProfilePicUrl())
                    .netBalance(entry.getValue())
                    .build());
        }

        return HomeFriendsResponse.builder()
                .totalOwedToYou(totalOwedToYou)
                .totalYouOwe(totalYouOwe)
                .displayCurrencyId(displayCurrencyId)
                .displayCurrencyCode(displayCurrency.getCode())
                .friends(friendSummaries)
                .build();
    }
}
