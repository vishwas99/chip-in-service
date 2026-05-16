package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dto.HomeFriendsResponse;
import com.chipIn.ChipIn.dto.HomeGroupsResponse;
import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.ExpensePayer;
import com.chipIn.ChipIn.entities.ExpenseSplit;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.GroupCurrency;
import com.chipIn.ChipIn.entities.GroupMember;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import com.chipIn.ChipIn.repository.GroupMemberRepository;
import com.chipIn.ChipIn.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HomeService {

    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseService expenseService;
    private final CurrencyRepository currencyRepository;
    private final UserRepository userRepository;
    private final CurrencyResolutionService currencyResolutionService;

    @Transactional(readOnly = true)
    public HomeGroupsResponse getHomeGroupsData(UUID userId, UUID displayCurrencyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Currency displayCurrency = resolveDisplayCurrency(displayCurrencyId, user);
        // Treat the request as if the viewer's "default currency" is the display currency
        // for the resolver's third hop. We achieve this by reassigning the user view.
        User viewerForDisplay = cloneWithDefaultCurrency(user, displayCurrency.getCurrencyId());

        List<GroupMember> memberships = groupMemberRepository.findByIdUserId(userId);

        BigDecimal totalOwedToYou = BigDecimal.ZERO;
        BigDecimal totalYouOwe = BigDecimal.ZERO;
        Map<String, BigDecimal> rawByCurrency = new LinkedHashMap<>();
        Set<String> missingRates = new LinkedHashSet<>();
        List<HomeGroupsResponse.GroupSummaryDto> groupSummaries = new ArrayList<>();

        List<Expense> allExpenses = expenseService.getExpensesForUser(userId);

        for (GroupMember membership : memberships) {
            Group group = membership.getGroup();
            List<Expense> groupExpenses = allExpenses.stream()
                    .filter(e -> e.getGroup().getGroupId().equals(group.getGroupId()))
                    .toList();

            CurrencyResolutionService.Aggregator viewerAgg = currencyResolutionService.newAggregator();
            LocalDateTime lastActivity = group.getCreatedAt();

            for (Expense expense : groupExpenses) {
                if (expense.getCreatedAt().isAfter(lastActivity)) lastActivity = expense.getCreatedAt();

                BigDecimal netInBucket = BigDecimal.ZERO;
                for (ExpensePayer payer : expense.getPayers()) {
                    if (payer.getUser().getUserid().equals(userId)) {
                        netInBucket = netInBucket.add(payer.getPaidAmount());
                    }
                }
                for (ExpenseSplit split : expense.getSplits()) {
                    if (split.getUser().getUserid().equals(userId)) {
                        netInBucket = netInBucket.subtract(split.getAmountOwed());
                    }
                }
                currencyResolutionService.accumulate(viewerAgg, netInBucket, expense.getCurrency(), group, viewerForDisplay);
            }

            BigDecimal netBalanceInGroupDefault = viewerAgg.getTotalInGroupDefault();
            BigDecimal netBalanceInDisplay = viewerAgg.getTotalInUserDefault();

            if (netBalanceInDisplay != null) {
                if (netBalanceInDisplay.signum() > 0) totalOwedToYou = totalOwedToYou.add(netBalanceInDisplay);
                else if (netBalanceInDisplay.signum() < 0) totalYouOwe = totalYouOwe.add(netBalanceInDisplay.abs());
            }
            for (Map.Entry<String, BigDecimal> entry : viewerAgg.getRawByCurrency().entrySet()) {
                rawByCurrency.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
            }
            missingRates.addAll(viewerAgg.getMissingRates());

            groupSummaries.add(HomeGroupsResponse.GroupSummaryDto.builder()
                    .groupId(group.getGroupId())
                    .groupName(group.getName())
                    .groupImageUrl(group.getImageUrl())
                    .groupCurrencyCode(group.getDefaultCurrency().getCode())
                    .netBalanceInGroupDefault(netBalanceInGroupDefault)
                    .netBalanceInDisplayCurrency(netBalanceInDisplay)
                    .rawByCurrency(roundMap(viewerAgg.getRawByCurrency()))
                    .lastActivity(lastActivity)
                    .build());
        }

        groupSummaries.sort((a, b) -> b.getLastActivity().compareTo(a.getLastActivity()));

        return HomeGroupsResponse.builder()
                .totalOwedToYou(totalOwedToYou.setScale(2, ROUNDING))
                .totalYouOwe(totalYouOwe.setScale(2, ROUNDING))
                .displayCurrencyId(displayCurrency.getCurrencyId())
                .displayCurrencyCode(displayCurrency.getCode())
                .rawByCurrency(roundMap(rawByCurrency))
                .missingRates(List.copyOf(missingRates))
                .groups(groupSummaries)
                .build();
    }

    @Transactional(readOnly = true)
    public HomeFriendsResponse getHomeFriendsData(UUID userId, UUID displayCurrencyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Currency displayCurrency = resolveDisplayCurrency(displayCurrencyId, user);
        User viewerForDisplay = cloneWithDefaultCurrency(user, displayCurrency.getCurrencyId());

        List<Expense> allExpenses = expenseService.getExpensesForUser(userId);

        Map<UUID, CurrencyResolutionService.Aggregator> perFriend = new HashMap<>();
        Set<String> missingRates = new LinkedHashSet<>();

        for (Expense expense : allExpenses) {
            Group group = expense.getGroup();
            GroupCurrency bucket = expense.getCurrency();

            // Greedy intra-expense settlement to derive viewer<->friend amounts in the bucket unit.
            Map<UUID, BigDecimal> bucketBalances = new HashMap<>();
            for (ExpensePayer payer : expense.getPayers()) {
                bucketBalances.merge(payer.getUser().getUserid(), payer.getPaidAmount(), BigDecimal::add);
            }
            for (ExpenseSplit split : expense.getSplits()) {
                bucketBalances.merge(split.getUser().getUserid(), split.getAmountOwed().negate(), BigDecimal::add);
            }

            List<Map.Entry<UUID, BigDecimal>> debtors = new ArrayList<>();
            List<Map.Entry<UUID, BigDecimal>> creditors = new ArrayList<>();
            for (Map.Entry<UUID, BigDecimal> e : bucketBalances.entrySet()) {
                if (e.getValue().signum() < 0) debtors.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
                else if (e.getValue().signum() > 0) creditors.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
            }
            debtors.sort(Map.Entry.comparingByValue());
            creditors.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            int i = 0, j = 0;
            while (i < debtors.size() && j < creditors.size()) {
                var debtor = debtors.get(i);
                var creditor = creditors.get(j);
                BigDecimal amount = debtor.getValue().abs().min(creditor.getValue());
                if (debtor.getKey().equals(userId)) {
                    CurrencyResolutionService.Aggregator agg = perFriend.computeIfAbsent(
                            creditor.getKey(), k -> currencyResolutionService.newAggregator());
                    currencyResolutionService.accumulate(agg, amount.negate(), bucket, group, viewerForDisplay);
                } else if (creditor.getKey().equals(userId)) {
                    CurrencyResolutionService.Aggregator agg = perFriend.computeIfAbsent(
                            debtor.getKey(), k -> currencyResolutionService.newAggregator());
                    currencyResolutionService.accumulate(agg, amount, bucket, group, viewerForDisplay);
                }
                debtor.setValue(debtor.getValue().add(amount));
                creditor.setValue(creditor.getValue().subtract(amount));
                if (debtor.getValue().signum() == 0) i++;
                if (creditor.getValue().signum() == 0) j++;
            }
        }

        BigDecimal totalOwedToYou = BigDecimal.ZERO;
        BigDecimal totalYouOwe = BigDecimal.ZERO;
        Map<String, BigDecimal> rawByCurrency = new LinkedHashMap<>();
        List<HomeFriendsResponse.FriendSummaryDto> friendSummaries = new ArrayList<>();

        for (Map.Entry<UUID, CurrencyResolutionService.Aggregator> entry : perFriend.entrySet()) {
            CurrencyResolutionService.Aggregator agg = entry.getValue();
            BigDecimal balance = agg.getTotalInUserDefault();
            missingRates.addAll(agg.getMissingRates());
            for (Map.Entry<String, BigDecimal> r : agg.getRawByCurrency().entrySet()) {
                rawByCurrency.merge(r.getKey(), r.getValue(), BigDecimal::add);
            }
            if (balance == null) continue;
            if (balance.signum() == 0) continue;

            User friend = userRepository.findById(entry.getKey()).orElse(null);
            if (friend == null) continue;

            if (balance.signum() > 0) totalOwedToYou = totalOwedToYou.add(balance);
            else totalYouOwe = totalYouOwe.add(balance.abs());

            friendSummaries.add(HomeFriendsResponse.FriendSummaryDto.builder()
                    .friendId(friend.getUserid())
                    .friendName(friend.getName())
                    .friendProfilePic(friend.getProfilePicUrl())
                    .netBalance(balance)
                    .rawByCurrency(roundMap(agg.getRawByCurrency()))
                    .build());
        }

        return HomeFriendsResponse.builder()
                .totalOwedToYou(totalOwedToYou.setScale(2, ROUNDING))
                .totalYouOwe(totalYouOwe.setScale(2, ROUNDING))
                .displayCurrencyId(displayCurrency.getCurrencyId())
                .displayCurrencyCode(displayCurrency.getCode())
                .rawByCurrency(roundMap(rawByCurrency))
                .missingRates(List.copyOf(missingRates))
                .friends(friendSummaries)
                .build();
    }

    private Currency resolveDisplayCurrency(UUID displayCurrencyId, User user) {
        if (displayCurrencyId != null) {
            return currencyRepository.findById(displayCurrencyId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display currency not found"));
        }
        if (user.getDefaultCurrencyId() != null) {
            Currency c = currencyRepository.findById(user.getDefaultCurrencyId()).orElse(null);
            if (c != null) return c;
        }
        return currencyRepository.findByCode("INR")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Default currency INR not configured"));
    }

    private User cloneWithDefaultCurrency(User user, UUID newDefault) {
        if (newDefault.equals(user.getDefaultCurrencyId())) return user;
        User copy = User.builder()
                .userid(user.getUserid())
                .defaultCurrencyId(newDefault)
                .build();
        return copy;
    }

    private Map<String, BigDecimal> roundMap(Map<String, BigDecimal> in) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : in.entrySet()) {
            out.put(entry.getKey(), entry.getValue().setScale(2, ROUNDING));
        }
        return out;
    }
}
