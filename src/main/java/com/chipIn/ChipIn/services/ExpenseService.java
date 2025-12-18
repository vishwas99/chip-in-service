package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dao.*;
import com.chipIn.ChipIn.dto.*;
import com.chipIn.ChipIn.dto.mapper.ExpenseMapper;
import com.chipIn.ChipIn.entities.*;
import com.chipIn.ChipIn.entities.Currency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExpenseService {

    @Autowired
    private GroupService groupService;

    @Autowired
    private ExpenseDao expenseDao;

    @Autowired
    private CurrencyExchangeDao currencyExchangeDao;

    @Autowired
    private UserToGroupDao userToGroupDao;

    @Autowired
    private SplitsDao splitsDao;

    @Autowired
    private UserService userService;

    @Autowired
    private ExpenseMapper expenseMapper;

    @Autowired
    private GroupDao groupDao;

    public void addExpense(ExpenseDto expenseDto){
        // Logic to add expense to the group

        // 1. Validate all users in split
        Set<UUID> groupMembers = groupService.getUserByGroupId(expenseDto.getGroupId());

        //1.1 Validate CurrencyId
        Currency currency = currencyExchangeDao.getCurrency(expenseDto.getCurrencyId());
        if(null == currency){
            throw new RuntimeException("Invalid Currency provided for the Expense");
        }


        // 2. Calculate exact amount split for each user
        Float totalAmount = expenseDto.getAmount();
        for(SplitDto splitDto : expenseDto.getExpenseSplit()){
            if(!groupMembers.contains(splitDto.getUserId())){
                throw new RuntimeException("Invalid Users in Expense split found");
            }
            if(!expenseDto.getExpenseOwner().equals(splitDto.getUserId())){
                splitDto.setAmount(splitDto.getAmount() * -1);
            }else{
                splitDto.setAmount(splitDto.getAmount());
            }
        }

        log.info("Expense List : {}", expenseDto.getExpenseSplit());

        // 3. Record expense in the group
        expenseDao.addExpenseToGroup(expenseMapper.toEntity(expenseDto));

        // 4. Update user-group table's moneyOwed column
        updateMoneyOwed(expenseDto);

    }

    public void updateMoneyOwed(ExpenseDto expenseDto){
        for(SplitDto splitDto: expenseDto.getExpenseSplit()){
            // For each user update total group expense
            userToGroupDao.setUserGroupMoneyOwed(splitDto.getUserId(), expenseDto.getGroupId(), splitDto.getAmount());
        }
    }

    public List<Expense> getExpensesByGroupId(UUID groupId){
        return expenseDao.getExpensesByGroupId(groupId);
    }

    public UserExpensesDto getExpensesByUserId(UUID userId) {
        UserExpensesDto userExpensesDto = new UserExpensesDto();
        userExpensesDto.setUserId(userId);

        // Global totals across all groups: currencyName -> net amount
        Map<String, Float> globalCurrencyMap = new HashMap<>();

        List<UserGroupResponse> userGroupResponseList = new ArrayList<>();

        // All groups the user is part of
        List<Group> userGroups = userToGroupDao.getGroupsObjectByUserId(userId);

        for (Group group : userGroups) {

            // All splits for this group (all users, all expenses)
            List<Split> groupSplits = expenseDao.getSplitsByGroupId(group.getGroupId());

            // Per-group net for this user: currencyName -> net amount
            Map<String, Float> currentGroupMap = new HashMap<>();

            for (Split split : groupSplits) {
                if (split == null || split.getExpense() == null || split.getExpense().getCurrency() == null)
                    continue;

                Expense expense = split.getExpense();
                String currencyName = expense.getCurrency().getCurrencyName();
                Float amount = split.getAmountOwed();
                if (amount == null) continue;

                UUID paidBy = expense.getPaidBy().getUserId();
                UUID participant = split.getUserId(); // or split.getUser().getUserId()

                // 1) This user's own splits ONLY when someone else paid
                if (participant.equals(userId) && !paidBy.equals(userId)) {
                    currentGroupMap.merge(currencyName, amount, Float::sum);
                    globalCurrencyMap.merge(currencyName, amount, Float::sum);
                }

// 2) This user is payer: others owe them -> invert other's amount
                if (paidBy.equals(userId) && !participant.equals(userId)) {
                    currentGroupMap.merge(currencyName, -amount, Float::sum);
                    globalCurrencyMap.merge(currencyName, -amount, Float::sum);
                }

            }

            // Build per-group DTO
            List<ExpenseResponseDto> groupExpenseList = new ArrayList<>();
            for (Map.Entry<String, Float> entry : currentGroupMap.entrySet()) {
                ExpenseResponseDto dto = new ExpenseResponseDto();
                dto.setCurrency(entry.getKey());
                dto.setMoneyOwed(entry.getValue()); // negative => user owes, positive => user is owed
                groupExpenseList.add(dto);
            }

            UserGroupResponse groupResponse = new UserGroupResponse();
            groupResponse.setGroup(group);
            groupResponse.setGroupExpense(groupExpenseList);

            userGroupResponseList.add(groupResponse);
        }

        // Build global totals DTO
        List<ExpenseResponseDto> globalMoneyOwedList = new ArrayList<>();
        for (Map.Entry<String, Float> entry : globalCurrencyMap.entrySet()) {
            ExpenseResponseDto dto = new ExpenseResponseDto();
            dto.setCurrency(entry.getKey());
            dto.setMoneyOwed(entry.getValue()); // negative => user owes, positive => user is owed
            globalMoneyOwedList.add(dto);
        }

        userExpensesDto.setMoneyOwedList(globalMoneyOwedList);
        userExpensesDto.setUserGroupResponses(userGroupResponseList);

        return userExpensesDto;
    }
    public List<Split> getExpensesByUserIdAndGroupId(UUID userId, UUID groupId){
        return expenseDao.getExpensesByUserIdAndGroupId(userId, groupId);
    }

    public UserSplitsDto getAllExpensesByUserIdGroupByUserId(UUID userId) {

        log.info("=== START: getAllExpensesByUserIdGroupByUserId for userId: {} ===", userId);

        // Creating Response Object
        UserSplitsDto userSplitsDto = new UserSplitsDto();
        userSplitsDto.setUser(userService.getUserById(userId));
        List<UserSplitHelperDto> userSplitHelperDtoList = new ArrayList<>();
        userSplitsDto.setExpenseList(userSplitHelperDtoList);
        log.debug("Initialized UserSplitsDto with user: {}", userSplitsDto.getUser());

        // Get All groups user is part of
        List<Group> allUserGroups = groupDao.getAllGroupsByUserId(userId);
        log.info("Found {} groups for user {}: {}", allUserGroups.size(), userId,
                allUserGroups.stream().map(g -> g.getGroupId() + "(" + g.getGroupName() + ")").collect(Collectors.joining(", ")));

        // Get All expenses for each group user is part of
        List<Expense> allUserExpenses = new ArrayList<>();
        for (Group curUserGroup : allUserGroups) {
            List<Expense> groupExpenses = getExpensesByGroupId(curUserGroup.getGroupId());
            log.debug("Group {} ({}): found {} expenses", curUserGroup.getGroupId(), curUserGroup.getGroupName(), groupExpenses.size());
            allUserExpenses.addAll(groupExpenses);
        }
        log.info("Total expenses across all groups: {} expenses", allUserExpenses.size());

        // Get ExpenseId - All expenseIds of the groups user is part of
        Set<UUID> expenseIds = allUserExpenses.stream()
                .map(Expense::getExpenseId)
                .collect(Collectors.toSet());
        log.info("Unique expense IDs: {} total", expenseIds.size());

        // Get All splits for all expensesIds
        List<Split> allUserSplits = splitsDao.getAllSplitsByExpenseIds(expenseIds);
        log.info("Found {} splits for expenses: {}", allUserSplits.size(), expenseIds.size());

        // Map of UserId - Map of Currency - MoneyOwed
        Map<UUID, Map<Currency, Float>> userMoneyMap = new HashMap<>();
        log.debug("Initialized userMoneyMap - processing {} splits", allUserSplits.size());

        int processedSplits = 0;
        int skippedSplits = 0;
        for (Split userSplit : allUserSplits) {
            processedSplits++;

            if (userSplit == null) {
                log.warn("Split #{} is NULL - skipping", processedSplits);
                skippedSplits++;
                continue;
            }

            Expense expense = userSplit.getExpense();
            if (expense == null || expense.getCurrency() == null) {
                log.warn("Split #{} - expense: {} or currency: {} is null - skipping",
                        processedSplits, expense != null ? expense.getExpenseId() : "NULL",
                        expense != null ? expense.getCurrency() : "NULL");
                skippedSplits++;
                continue;
            }

            UUID paidByUserId = expense.getPaidBy().getUserId();
            if (paidByUserId == null) {
                log.warn("Split #{} expense {} - paidByUserId is NULL - skipping",
                        processedSplits, expense.getExpenseId());
                skippedSplits++;
                continue;
            }

            UUID splitUserId = userSplit.getUserId();
            Float curMoneyOwed = userSplit.getAmountOwed();
            if (curMoneyOwed == null) {
                log.warn("Split #{} user {} - amountOwed is NULL - skipping",
                        processedSplits, splitUserId);
                skippedSplits++;
                continue;
            }

            Currency curCurrency = expense.getCurrency();
            log.debug("Split #{}: expense={}, paidBy={}, splitUser={}, amount={}, currency={}",
                    processedSplits, expense.getExpenseId(), paidByUserId, splitUserId, curMoneyOwed, curCurrency);

            // money from targetUser → payer
            if (splitUserId.equals(userId)) {
                Map<Currency, Float> curCurrencyMap = userMoneyMap.computeIfAbsent(paidByUserId, k -> new HashMap<>());
                Float previousAmount = curCurrencyMap.getOrDefault(curCurrency, 0f);
                curCurrencyMap.merge(curCurrency, curMoneyOwed, Float::sum);
                log.debug("  → OWE {}: added {} {} (was: {}) to {} total: {}",
                        paidByUserId, curMoneyOwed, curCurrency, previousAmount,
                        curCurrencyMap.get(curCurrency), paidByUserId);
            }

            // money from others → targetUser (targetUser is payer)
            if (paidByUserId.equals(userId) && !splitUserId.equals(userId)) {
                Map<Currency, Float> curCurrencyMap = userMoneyMap.computeIfAbsent(splitUserId, k -> new HashMap<>());
                Float previousAmount = curCurrencyMap.getOrDefault(curCurrency, 0f);
                curCurrencyMap.merge(curCurrency, -curMoneyOwed, Float::sum);
                log.debug("  → OWED BY {}: added -{} {} (was: {}) to {} total: {}",
                        splitUserId, curMoneyOwed, curCurrency, previousAmount,
                        curCurrencyMap.get(curCurrency), splitUserId);
            }
        }
        log.info("Split processing complete: {} processed, {} skipped", processedSplits, skippedSplits);

        // Remove Current User's data from userMoneyMap
        userMoneyMap.remove(userId);
        log.info("userMoneyMap after removing current user ({}): {} users", userId, userMoneyMap.size());

        // Log final userMoneyMap state
        log.info("=== FINAL userMoneyMap ===");
        for (Map.Entry<UUID, Map<Currency, Float>> entry : userMoneyMap.entrySet()) {
            String currencyBreakdown = entry.getValue().entrySet().stream()
                    .map(c -> String.format("%s:%.2f", c.getKey(), c.getValue()))
                    .collect(Collectors.joining(", "));
            log.info("User {}: {}", entry.getKey(), currencyBreakdown);
        }

        // Creating Response
        for (Map.Entry<UUID, Map<Currency, Float>> entry : userMoneyMap.entrySet()) {
            UUID curUserId = entry.getKey();
            UserDto userDto = userService.getUserById(curUserId);
            List<MoneyOwedDto> curMoneyOwedDtos = new ArrayList<>();

            log.debug("Processing user {}: {}", curUserId, userDto.getName());

            for (Map.Entry<Currency, Float> currencyEntry : entry.getValue().entrySet()) {
                MoneyOwedDto moneyOwedDto = new MoneyOwedDto(currencyEntry.getValue(), currencyEntry.getKey());
                curMoneyOwedDtos.add(moneyOwedDto);
                log.debug("  Added MoneyOwedDto: {} {}", currencyEntry.getValue(), currencyEntry.getKey());
            }

            UserSplitHelperDto helperDto = new UserSplitHelperDto(userDto, curMoneyOwedDtos);
            userSplitHelperDtoList.add(helperDto);
            log.debug("Added UserSplitHelperDto for user {} with {} currency entries", curUserId, curMoneyOwedDtos.size());
        }

        log.info("Final response: {} UserSplitHelperDto entries", userSplitHelperDtoList.size());
        log.info("=== END: getAllExpensesByUserIdGroupByUserId for userId: {} ===", userId);

        return userSplitsDto;
    }


}
