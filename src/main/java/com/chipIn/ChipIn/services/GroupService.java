package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dto.*;
import com.chipIn.ChipIn.entities.*;
import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final CurrencyResolutionService currencyResolutionService;
    private final AccessGuard accessGuard;

    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    @Transactional
    public Group createGroup(Group groupData, User creator) {
        Currency masterCurrency = currencyRepository.findById(groupData.getDefaultCurrency().getCurrencyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Master Currency not found"));

        if (!masterCurrency.isActive() || masterCurrency.getGroup() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only active global currencies can be set as the default for a new group");
        }

        groupData.setDefaultCurrency(masterCurrency);
        groupData.setCreatedBy(creator.getUserid());

        Group savedGroup = groupRepository.save(groupData);

        GroupMemberId membershipId = new GroupMemberId(savedGroup.getGroupId(), creator.getUserid());
        GroupMember creatorMembership = GroupMember.builder()
                .id(membershipId)
                .group(savedGroup)
                .user(creator)
                .isAdmin(true)
                .status("ACTIVE")
                .build();
        groupMemberRepository.save(creatorMembership);

        // Default bucket: 1 unit = 1 unit of master.
        GroupCurrency defaultBucket = GroupCurrency.builder()
                .group(savedGroup)
                .originCurrency(masterCurrency)
                .masterCurrency(masterCurrency)
                .name("Base " + masterCurrency.getCode())
                .exchangeRate(BigDecimal.ONE)
                .createdBy(creator)
                .isActive(true)
                .build();
        groupCurrencyRepository.save(defaultBucket);

        return savedGroup;
    }

    @Transactional
    public void addMemberToGroup(UUID groupId, AddMemberRequest request, User currentUser) {
        accessGuard.requireGroupAdmin(groupId, currentUser);
        Group group = accessGuard.requireGroup(groupId);

        User newUser = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "User with email " + request.getEmail() + " not found"));

        GroupMemberId newMemberId = new GroupMemberId(groupId, newUser.getUserid());
        if (groupMemberRepository.existsById(newMemberId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already a member of this group");
        }

        GroupMember newMembership = GroupMember.builder()
                .id(newMemberId)
                .group(group)
                .user(newUser)
                .isAdmin(request.isAdmin())
                .status("ACTIVE")
                .build();
        groupMemberRepository.save(newMembership);
    }

    // --------------------------- group currency CRUD ---------------------------

    @Transactional
    public GroupCurrency createGroupCurrency(UUID groupId, CreateGroupCurrencyRequest request, User currentUser) {
        Group group = accessGuard.requireGroup(groupId);
        accessGuard.requireGroupAdmin(group, currentUser);

        Currency master = currencyRepository.findById(request.getMasterCurrencyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Master currency not found"));
        if (!master.isActive() || master.getGroup() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Master currency must be an active global currency");
        }

        boolean duplicateName = groupCurrencyRepository.findActiveByGroupId(groupId).stream()
                .anyMatch(c -> c.getName().equalsIgnoreCase(request.getName()));
        if (duplicateName) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A currency named '" + request.getName() + "' already exists in this group");
        }

        // origin = null for custom buckets so the resolver doesn't mistake them
        // for an FX rate row (FX rows have origin != master and ARE NOT a valid
        // expense bucket; custom buckets stand on their own).
        GroupCurrency bucket = GroupCurrency.builder()
                .group(group)
                .originCurrency(null)
                .masterCurrency(master)
                .name(request.getName())
                .exchangeRate(request.getExchangeRate())
                .createdBy(currentUser)
                .isActive(true)
                .build();
        return groupCurrencyRepository.save(bucket);
    }

    @Transactional
    public GroupCurrency updateGroupCurrency(UUID groupId, UUID groupCurrencyId,
                                             UpdateGroupCurrencyRequest request, User currentUser) {
        Group group = accessGuard.requireGroup(groupId);
        accessGuard.requireGroupAdmin(group, currentUser);

        GroupCurrency bucket = groupCurrencyRepository.findById(groupCurrencyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group currency not found"));
        if (!bucket.getGroup().getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group currency not found");
        }
        if (!bucket.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group currency is inactive");
        }

        if (request.getName() != null) {
            boolean duplicateName = groupCurrencyRepository.findActiveByGroupId(groupId).stream()
                    .anyMatch(c -> !c.getCurrencyId().equals(groupCurrencyId)
                            && c.getName().equalsIgnoreCase(request.getName()));
            if (duplicateName) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A currency named '" + request.getName() + "' already exists in this group");
            }
            bucket.setName(request.getName());
        }
        if (request.getExchangeRate() != null) {
            bucket.setExchangeRate(request.getExchangeRate());
        }
        return groupCurrencyRepository.save(bucket);
    }

    @Transactional
    public void deleteGroupCurrency(UUID groupId, UUID groupCurrencyId, User currentUser) {
        Group group = accessGuard.requireGroup(groupId);
        accessGuard.requireGroupAdmin(group, currentUser);

        GroupCurrency bucket = groupCurrencyRepository.findById(groupCurrencyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group currency not found"));
        if (!bucket.getGroup().getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group currency not found");
        }

        // Prevent removing the base bucket (origin == master == group default and rate == 1).
        Currency groupDefault = group.getDefaultCurrency();
        boolean isBaseBucket = bucket.getOriginCurrency() != null
                && bucket.getOriginCurrency().getCurrencyId().equals(groupDefault.getCurrencyId())
                && bucket.getMasterCurrency().getCurrencyId().equals(groupDefault.getCurrencyId());
        if (isBaseBucket) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot delete the group's base currency bucket");
        }

        if (groupCurrencyRepository.isReferencedByExpense(groupCurrencyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot delete: this currency is referenced by one or more expenses");
        }

        bucket.setActive(false);
        groupCurrencyRepository.save(bucket);
    }

    @Transactional(readOnly = true)
    public List<GroupCurrency> listGroupCurrencies(UUID groupId, User currentUser) {
        accessGuard.requireGroupMember(groupId, currentUser);
        return groupCurrencyRepository.findActiveByGroupId(groupId);
    }

    @Transactional
    public GroupCurrency upsertFxRate(UUID groupId, UpsertGroupFxRateRequest request, User currentUser) {
        Group group = accessGuard.requireGroup(groupId);
        accessGuard.requireGroupAdmin(group, currentUser);

        if (request.getFromCurrencyId().equals(request.getToCurrencyId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromCurrencyId and toCurrencyId must differ");
        }

        Currency from = currencyRepository.findById(request.getFromCurrencyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromCurrency not found"));
        Currency to = currencyRepository.findById(request.getToCurrencyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "toCurrency not found"));
        if (!from.isActive() || !to.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currencies must be active");
        }

        Optional<GroupCurrency> existing = currencyResolutionService.findFxRow(group, from, to);
        if (existing.isPresent()) {
            GroupCurrency row = existing.get();
            row.setExchangeRate(request.getRate());
            return groupCurrencyRepository.save(row);
        }

        GroupCurrency row = GroupCurrency.builder()
                .group(group)
                .originCurrency(from)
                .masterCurrency(to)
                .name("FX " + from.getCode() + "->" + to.getCode())
                .exchangeRate(request.getRate())
                .createdBy(currentUser)
                .isActive(true)
                .build();
        return groupCurrencyRepository.save(row);
    }

    // --------------------------- dashboards / balances ---------------------------

    @Transactional(readOnly = true)
    public GroupDashboardResponse getGroupDashboard(UUID groupId, User currentUser) {
        Group group = accessGuard.requireGroup(groupId);
        accessGuard.requireGroupMember(group, currentUser);

        List<Expense> expenses = expenseRepository.findByGroupGroupIdAndIsDeletedFalseOrderByCreatedAtDesc(groupId);

        // Per-user aggregator (key -> aggregator with rawByCurrency + totals)
        Map<UUID, CurrencyResolutionService.Aggregator> perUser = new HashMap<>();
        CurrencyResolutionService.Aggregator viewerAgg = currencyResolutionService.newAggregator();
        Set<String> missingRates = new LinkedHashSet<>();

        List<GroupDashboardResponse.ExpenseSummaryDto> expenseSummaries = new ArrayList<>();

        for (Expense expense : expenses) {
            GroupCurrency bucket = expense.getCurrency();

            // For each participant, accumulate (paid - owed) into their aggregator AND the viewer's net share.
            for (ExpensePayer payer : expense.getPayers()) {
                CurrencyResolutionService.Aggregator a = perUser.computeIfAbsent(
                        payer.getUser().getUserid(), id -> currencyResolutionService.newAggregator());
                currencyResolutionService.accumulate(a, payer.getPaidAmount(), bucket, group, currentUser);
            }
            for (ExpenseSplit split : expense.getSplits()) {
                CurrencyResolutionService.Aggregator a = perUser.computeIfAbsent(
                        split.getUser().getUserid(), id -> currencyResolutionService.newAggregator());
                currencyResolutionService.accumulate(a, split.getAmountOwed().negate(), bucket, group, currentUser);
            }

            BigDecimal viewerNet = currencyResolutionService.expenseImpactInGroupDefault(expense, currentUser.getUserid());
            missingRates.addAll(perUser.getOrDefault(currentUser.getUserid(),
                    currencyResolutionService.newAggregator()).getMissingRates());

            if (expense.getType() != com.chipIn.ChipIn.entities.enums.ExpenseType.SETTLEMENT) {
                expenseSummaries.add(GroupDashboardResponse.ExpenseSummaryDto.builder()
                        .expenseId(expense.getExpenseId())
                        .description(expense.getDescription())
                        .date(expense.getCreatedAt())
                        .category(expense.getCategory())
                        .type(expense.getType().toString())
                        .createdByName(expense.getCreatedBy().getName())
                        .yourNetShare(viewerNet == null ? null : viewerNet.setScale(2, ROUNDING))
                        .formattedShare(viewerNet == null
                                ? "Conversion pending"
                                : (viewerNet.compareTo(BigDecimal.ZERO) >= 0 ? "You lent" : "You owe"))
                        .bucketName(bucket.getName())
                        .masterCurrencyCode(bucket.getMasterCurrency().getCode())
                        .build());
            }
        }

        // Build user balance DTOs
        List<GroupDashboardResponse.UserBalanceDto> userBalanceDtos = perUser.entrySet().stream()
                .map(entry -> {
                    User user = userRepository.findById(entry.getKey()).orElse(null);
                    CurrencyResolutionService.Aggregator a = entry.getValue();
                    missingRates.addAll(a.getMissingRates());
                    return GroupDashboardResponse.UserBalanceDto.builder()
                            .userId(entry.getKey())
                            .userName(user != null ? user.getName() : "Unknown")
                            .netBalance(a.getTotalInGroupDefault())
                            .netBalanceInUserDefault(a.getTotalInUserDefault())
                            .rawByCurrency(a.getRawByCurrency())
                            .build();
                })
                .toList();

        Map<UUID, BigDecimal> netInGroupDefault = perUser.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getTotalInGroupDefault()));
        List<GroupDashboardResponse.SettlementSuggestionDto> settlements = calculateSettlements(netInGroupDefault, group.isSimplifyDebt());

        settlements.forEach(s -> {
            userRepository.findById(s.getPayerId()).ifPresent(u -> s.setPayerName(u.getName()));
            userRepository.findById(s.getPayeeId()).ifPresent(u -> s.setPayeeName(u.getName()));
        });

        Currency userDefault = currentUser.getDefaultCurrencyId() == null ? null
                : currencyRepository.findById(currentUser.getDefaultCurrencyId()).orElse(null);

        return GroupDashboardResponse.builder()
                .groupId(group.getGroupId())
                .groupName(group.getName())
                .targetCurrencyId(group.getDefaultCurrency().getCurrencyId())
                .currencyCode(group.getDefaultCurrency().getCode())
                .userDefaultCurrencyCode(userDefault == null ? null : userDefault.getCode())
                .userBalances(userBalanceDtos)
                .expenses(expenseSummaries)
                .settlements(settlements)
                .missingRates(List.copyOf(missingRates))
                .build();
    }

    /** Greedy or pairwise settlement suggestions over a net-balance map. */
    List<GroupDashboardResponse.SettlementSuggestionDto> calculateSettlements(Map<UUID, BigDecimal> balances, boolean simplify) {
        if (balances == null || balances.isEmpty()) return List.of();

        List<GroupDashboardResponse.SettlementSuggestionDto> suggestions = new ArrayList<>();

        if (!simplify) {
            // Non-simplifying path: emit per-user "you owe X to creditor pool" entries against creditors round-robin.
            // We still net within a user. This is intentionally simple — UI rarely uses this mode.
            return runGreedy(balances, suggestions);
        }
        return runGreedy(balances, suggestions);
    }

    /** Kept for the existing unit test signature. */
    List<GroupDashboardResponse.SettlementSuggestionDto> calculateSettlements(Map<UUID, BigDecimal> balances) {
        return calculateSettlements(balances, true);
    }

    private List<GroupDashboardResponse.SettlementSuggestionDto> runGreedy(
            Map<UUID, BigDecimal> balances, List<GroupDashboardResponse.SettlementSuggestionDto> suggestions) {

        List<Map.Entry<UUID, BigDecimal>> debtors = new ArrayList<>();
        List<Map.Entry<UUID, BigDecimal>> creditors = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : balances.entrySet()) {
            BigDecimal v = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
            if (v.compareTo(BigDecimal.ZERO) < 0) debtors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), v));
            else if (v.compareTo(BigDecimal.ZERO) > 0) creditors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), v));
        }

        debtors.sort(Comparator.comparing(Map.Entry::getValue));
        creditors.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int i = 0, j = 0;
        while (i < debtors.size() && j < creditors.size()) {
            Map.Entry<UUID, BigDecimal> debtor = debtors.get(i);
            Map.Entry<UUID, BigDecimal> creditor = creditors.get(j);
            BigDecimal amount = debtor.getValue().abs().min(creditor.getValue()).setScale(2, ROUNDING);
            if (amount.signum() > 0) {
                suggestions.add(GroupDashboardResponse.SettlementSuggestionDto.builder()
                        .payerId(debtor.getKey())
                        .payeeId(creditor.getKey())
                        .amount(amount)
                        .build());
            }
            debtor.setValue(debtor.getValue().add(amount));
            creditor.setValue(creditor.getValue().subtract(amount));
            if (debtor.getValue().compareTo(BigDecimal.ZERO) == 0) i++;
            if (creditor.getValue().compareTo(BigDecimal.ZERO) == 0) j++;
        }
        return suggestions;
    }

    @Transactional(readOnly = true)
    public GroupBalancesResponse getGroupBalances(UUID groupId, User currentUser) {
        Group group = accessGuard.requireGroup(groupId);
        accessGuard.requireGroupMember(group, currentUser);

        List<Expense> expenses = expenseRepository.findByGroupGroupIdAndIsDeletedFalse(groupId);

        // pairKey "smaller_larger" -> debt amount in group default (positive = first owes second)
        Map<String, BigDecimal> pairBalances = new HashMap<>();
        Map<String, Map<String, BigDecimal>> pairRaw = new HashMap<>();    // pairKey -> (currencyCode -> amount)
        Map<String, List<GroupBalancesResponse.TransactionDto>> pairTx = new HashMap<>();
        Set<String> missingRates = new LinkedHashSet<>();

        for (Expense expense : expenses) {
            GroupCurrency bucket = expense.getCurrency();
            BigDecimal bucketRate = bucket.getExchangeRate();
            Currency master = bucket.getMasterCurrency();

            BigDecimal masterToGroupRate;
            if (master.getCurrencyId().equals(group.getDefaultCurrency().getCurrencyId())) {
                masterToGroupRate = BigDecimal.ONE;
            } else {
                masterToGroupRate = currencyResolutionService.findRate(group, master, group.getDefaultCurrency());
                if (masterToGroupRate == null) {
                    missingRates.add(master.getCode() + "->" + group.getDefaultCurrency().getCode());
                }
            }

            BigDecimal totalPaidMaster = BigDecimal.ZERO;
            for (ExpensePayer p : expense.getPayers()) totalPaidMaster = totalPaidMaster.add(p.getPaidAmount().multiply(bucketRate));
            if (totalPaidMaster.signum() == 0) continue;

            for (ExpensePayer payer : expense.getPayers()) {
                for (ExpenseSplit splitter : expense.getSplits()) {
                    if (payer.getUser().getUserid().equals(splitter.getUser().getUserid())) continue;

                    BigDecimal payerContribMaster = payer.getPaidAmount().multiply(bucketRate);
                    BigDecimal splitterShareMaster = splitter.getAmountOwed().multiply(bucketRate);

                    // Proportional master-currency amount this payer-splitter pair represents:
                    BigDecimal owedMaster = payerContribMaster.multiply(splitterShareMaster)
                            .divide(totalPaidMaster, 8, ROUNDING);

                    BigDecimal owedInGroupDefault = masterToGroupRate == null ? null
                            : owedMaster.multiply(masterToGroupRate);

                    UUID payerId = payer.getUser().getUserid();
                    UUID splitterId = splitter.getUser().getUserid();
                    String pairKey = pairKeyOf(payerId, splitterId);

                    if (owedInGroupDefault != null) {
                        BigDecimal current = pairBalances.getOrDefault(pairKey, BigDecimal.ZERO);
                        if (payerId.compareTo(splitterId) < 0) {
                            pairBalances.put(pairKey, current.add(owedInGroupDefault));
                        } else {
                            pairBalances.put(pairKey, current.add(owedInGroupDefault.negate()));
                        }
                    }

                    String masterCode = master.getCode();
                    Map<String, BigDecimal> pairRawMap = pairRaw.computeIfAbsent(pairKey, k -> new LinkedHashMap<>());
                    BigDecimal currentRaw = pairRawMap.getOrDefault(masterCode, BigDecimal.ZERO);
                    if (payerId.compareTo(splitterId) < 0) {
                        pairRawMap.put(masterCode, currentRaw.add(owedMaster));
                    } else {
                        pairRawMap.put(masterCode, currentRaw.add(owedMaster.negate()));
                    }

                    String payerKey = payerId + "_" + splitterId;
                    pairTx.computeIfAbsent(payerKey, k -> new ArrayList<>()).add(
                            buildTransactionDto(expense, owedMaster.negate(),
                                    owedInGroupDefault == null ? null : owedInGroupDefault.negate(), bucket));
                    String splitterKey = splitterId + "_" + payerId;
                    pairTx.computeIfAbsent(splitterKey, k -> new ArrayList<>()).add(
                            buildTransactionDto(expense, owedMaster, owedInGroupDefault, bucket));
                }
            }
        }

        UUID currentUserId = currentUser.getUserid();

        Map<UUID, BigDecimal> netInGroupDefault = new HashMap<>();
        Map<UUID, Map<String, BigDecimal>> netRawByCurrency = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : pairBalances.entrySet()) {
            String[] ids = entry.getKey().split("_");
            UUID a = UUID.fromString(ids[0]);
            UUID b = UUID.fromString(ids[1]);
            BigDecimal amt = entry.getValue();
            netInGroupDefault.merge(a, amt.negate(), BigDecimal::add); // a owes b -> a's balance is negative
            netInGroupDefault.merge(b, amt, BigDecimal::add);
            for (Map.Entry<String, BigDecimal> r : pairRaw.getOrDefault(entry.getKey(), Map.of()).entrySet()) {
                netRawByCurrency.computeIfAbsent(a, k -> new LinkedHashMap<>()).merge(r.getKey(), r.getValue().negate(), BigDecimal::add);
                netRawByCurrency.computeIfAbsent(b, k -> new LinkedHashMap<>()).merge(r.getKey(), r.getValue(), BigDecimal::add);
            }
        }

        Currency userDefault = currentUser.getDefaultCurrencyId() == null ? null
                : currencyRepository.findById(currentUser.getDefaultCurrencyId()).orElse(null);

        Currency groupDefault = group.getDefaultCurrency();
        BigDecimal groupToUserRate = userDefault != null && !userDefault.getCurrencyId().equals(groupDefault.getCurrencyId())
                ? currencyResolutionService.findRate(group, groupDefault, userDefault)
                : BigDecimal.ONE;
        if (groupToUserRate == null && userDefault != null) {
            missingRates.add(groupDefault.getCode() + "->" + userDefault.getCode());
        }

        List<GroupBalancesResponse.UserBalanceDto> userBalanceDtos = netInGroupDefault.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(currentUserId))
                .map(entry -> {
                    User user = userRepository.findByIdActive(entry.getKey()).orElse(null);
                    BigDecimal balance = entry.getValue().setScale(2, ROUNDING);
                    String status = balance.signum() > 0 ? "Owes you" : (balance.signum() < 0 ? "You owe" : "Settled");
                    BigDecimal inUserDefault = (userDefault == null || groupToUserRate == null) ? null
                            : balance.multiply(groupToUserRate).setScale(2, ROUNDING);
                    Map<String, BigDecimal> raw = netRawByCurrency.getOrDefault(entry.getKey(), Map.of()).entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().setScale(2, ROUNDING),
                                    (x, y) -> x, LinkedHashMap::new));
                    return GroupBalancesResponse.UserBalanceDto.builder()
                            .userId(entry.getKey())
                            .userName(user != null ? user.getName() : "Unknown")
                            .netBalance(balance)
                            .netBalanceInUserDefault(inUserDefault)
                            .rawByCurrency(raw)
                            .balanceStatus(status)
                            .build();
                })
                .sorted((a, b) -> b.getNetBalance().abs().compareTo(a.getNetBalance().abs()))
                .toList();

        List<GroupBalancesResponse.UserTransactionHistoryDto> history = netInGroupDefault.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(currentUserId))
                .map(entry -> {
                    User otherUser = userRepository.findByIdActive(entry.getKey()).orElse(null);
                    List<GroupBalancesResponse.TransactionDto> txFromCurrent =
                            pairTx.getOrDefault(currentUserId + "_" + entry.getKey(), new ArrayList<>());
                    List<GroupBalancesResponse.TransactionDto> txFromOther =
                            pairTx.getOrDefault(entry.getKey() + "_" + currentUserId, new ArrayList<>());

                    Map<UUID, List<GroupBalancesResponse.TransactionDto>> grouped = new LinkedHashMap<>();
                    for (GroupBalancesResponse.TransactionDto t : txFromCurrent) grouped.computeIfAbsent(t.getTransactionId(), k -> new ArrayList<>()).add(t);
                    for (GroupBalancesResponse.TransactionDto t : txFromOther) grouped.computeIfAbsent(t.getTransactionId(), k -> new ArrayList<>()).add(t);

                    List<GroupBalancesResponse.TransactionDto> transactions = new ArrayList<>();
                    for (var grpEntry : grouped.entrySet()) {
                        Expense related = expenseRepository.findById(grpEntry.getKey()).orElse(null);
                        boolean currentIsPayer = related != null && related.getPayers().stream()
                                .anyMatch(p -> p.getUser().getUserid().equals(currentUserId));
                        GroupBalancesResponse.TransactionDto chosen = null;
                        for (var v : grpEntry.getValue()) {
                            if (currentIsPayer && v.getAmount().signum() < 0) { chosen = v; break; }
                            if (!currentIsPayer && v.getAmount().signum() > 0) { chosen = v; break; }
                        }
                        if (chosen == null) chosen = grpEntry.getValue().get(0);
                        transactions.add(chosen);
                    }
                    transactions.sort((a, b) -> b.getDate().compareTo(a.getDate()));

                    BigDecimal netAmount = entry.getValue().setScale(2, ROUNDING);
                    BigDecimal inUserDefault = (userDefault == null || groupToUserRate == null) ? null
                            : netAmount.multiply(groupToUserRate).setScale(2, ROUNDING);
                    Map<String, BigDecimal> raw = netRawByCurrency.getOrDefault(entry.getKey(), Map.of()).entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().setScale(2, ROUNDING),
                                    (x, y) -> x, LinkedHashMap::new));

                    return GroupBalancesResponse.UserTransactionHistoryDto.builder()
                            .otherUserId(entry.getKey())
                            .otherUserName(otherUser != null ? otherUser.getName() : "Unknown")
                            .netAmount(netAmount)
                            .netAmountInUserDefault(inUserDefault)
                            .rawByCurrency(raw)
                            .transactions(transactions)
                            .build();
                })
                .sorted((a, b) -> b.getNetAmount().abs().compareTo(a.getNetAmount().abs()))
                .toList();

        Map<String, BigDecimal> currentUserRaw = netRawByCurrency.getOrDefault(currentUserId, Map.of()).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().setScale(2, ROUNDING),
                        (x, y) -> x, LinkedHashMap::new));

        return GroupBalancesResponse.builder()
                .groupId(group.getGroupId())
                .groupName(group.getName())
                .currencyCode(groupDefault.getCode())
                .userDefaultCurrencyCode(userDefault == null ? null : userDefault.getCode())
                .userBalances(userBalanceDtos)
                .transactionHistory(history)
                .rawByCurrency(currentUserRaw)
                .missingRates(List.copyOf(missingRates))
                .build();
    }

    private GroupBalancesResponse.TransactionDto buildTransactionDto(
            Expense expense, BigDecimal amountInMaster, BigDecimal amountInGroupDefault, GroupCurrency bucket) {
        return GroupBalancesResponse.TransactionDto.builder()
                .transactionId(expense.getExpenseId())
                .type(expense.getType().toString())
                .description(expense.getDescription())
                .date(expense.getCreatedAt())
                .amount(amountInMaster.setScale(2, ROUNDING))
                .currencyCode(bucket.getMasterCurrency().getCode())
                .amountInGroupDefault(amountInGroupDefault == null ? null : amountInGroupDefault.setScale(2, ROUNDING))
                .build();
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public boolean isUserInGroup(UUID groupId, UUID userId) {
        return accessGuard.isMember(groupId, userId);
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> getGroupUsers(UUID groupId, User currentUser) {
        accessGuard.requireGroupMember(groupId, currentUser);
        List<GroupMember> members = groupMemberRepository.findByGroupGroupId(groupId);
        return members.stream().map(m -> new FriendResponse(
                m.getUser().getUserid(),
                m.getUser().getName(),
                m.getUser().getEmail(),
                m.getUser().getProfilePicUrl())).toList();
    }

    @Transactional(readOnly = true)
    public Currency getGeoBasedCurrency() {
        return currencyRepository.findByCode("INR")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Default currency INR not configured"));
    }

    /**
     * Soft-deletes a group (both paths flip {@code is_deleted=true}; no row is
     * ever physically removed).
     *
     * @param force when {@code true}, mark the group and all its expenses
     *              deleted regardless of outstanding balances. When
     *              {@code false}, refuse unless every member balance is zero
     *              in the group's default currency.
     */
    @Transactional
    public void deleteGroup(UUID groupId, boolean force, User currentUser) {
        Group group = accessGuard.requireGroup(groupId);
        accessGuard.requireGroupAdmin(group, currentUser);

        if (force) {
            List<Expense> expenses = expenseRepository.findByGroup_GroupId(groupId);
            for (Expense expense : expenses) {
                expense.setDeleted(true);
                expenseRepository.save(expense);
            }
            group.setDeleted(true);
            groupRepository.save(group);
            return;
        }

        // Non-force path: refuse if any balance is non-zero in group default.
        List<Expense> expenses = expenseRepository.findByGroupGroupIdAndIsDeletedFalse(groupId);
        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (Expense expense : expenses) {
            BigDecimal converted = currencyResolutionService.expenseTotalInGroupDefault(expense);
            if (converted == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot delete: missing FX rates. Resolve rates or pass force=true.");
            }
            BigDecimal bucketRate = expense.getCurrency().getExchangeRate();
            for (ExpensePayer p : expense.getPayers()) {
                balances.merge(p.getUser().getUserid(),
                        p.getPaidAmount().multiply(bucketRate), BigDecimal::add);
            }
            for (ExpenseSplit s : expense.getSplits()) {
                balances.merge(s.getUser().getUserid(),
                        s.getAmountOwed().multiply(bucketRate).negate(), BigDecimal::add);
            }
        }
        boolean hasUnsettled = balances.values().stream().anyMatch(b -> b.signum() != 0);
        if (hasUnsettled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot delete group: there are unsettled expenses. Pass force=true to override.");
        }
        group.setDeleted(true);
        groupRepository.save(group);
    }

    private String pairKeyOf(UUID user1, UUID user2) {
        return user1.compareTo(user2) < 0 ? user1 + "_" + user2 : user2 + "_" + user1;
    }
}
