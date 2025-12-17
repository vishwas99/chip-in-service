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
        // 1. Changed return type

        UserExpensesDto userExpensesDto = new UserExpensesDto();
        userExpensesDto.setUserId(userId);

        // This map stores the GLOBAL totals across all groups (Currency Name -> Total Amount)
        Map<String, Float> globalCurrencyMap = new HashMap<>();

        List<UserGroupResponse> userGroupResponseList = new ArrayList<>();

        // 2. Fetch all groups the user is part of
        List<Group> userGroups = userToGroupDao.getGroupsObjectByUserId(userId);

        // 3. Iterate over each group
        for (Group group : userGroups) {

            // Fetch splits for this specific group
            // Ensure you are calling the DAO method we fixed earlier
            List<Split> userSplits = getExpensesByUserIdAndGroupId(userId, group.getGroupId());

            // Map for THIS group's totals only
            Map<String, Float> currentGroupMap = new HashMap<>();

            for (Split split : userSplits) {
                // Null checks to prevent crashes
                if (split.getExpense() != null && split.getExpense().getCurrency() != null) {
                    String currencyName = split.getExpense().getCurrency().getCurrencyName();
                    Float amount = split.getAmountOwed();

                    if (amount != null) {
                        // Accumulate for current group
                        currentGroupMap.merge(currencyName, amount, Float::sum);

                        // Accumulate for global total
                        globalCurrencyMap.merge(currencyName, amount, Float::sum);
                    }
                }
            }

            // Convert the Group Map to List<GroupExpenseDto>
            List<ExpenseResponseDto> groupExpenseList = new ArrayList<>();
            for (Map.Entry<String, Float> entry : currentGroupMap.entrySet()) {
                ExpenseResponseDto dto = new ExpenseResponseDto();
                dto.setCurrency(entry.getKey());
                dto.setMoneyOwed(entry.getValue());
                groupExpenseList.add(dto);
            }

            // Build the UserGroupResponse object
            UserGroupResponse groupResponse = new UserGroupResponse();
            groupResponse.setGroup(group);
            groupResponse.setGroupExpense(groupExpenseList);

            userGroupResponseList.add(groupResponse);
        }

        // 4. Convert the Global Map to List<GroupExpenseDto>
        List<ExpenseResponseDto> globalMoneyOwedList = new ArrayList<>();
        for (Map.Entry<String, Float> entry : globalCurrencyMap.entrySet()) {
            ExpenseResponseDto dto = new ExpenseResponseDto();
            dto.setCurrency(entry.getKey());
            dto.setMoneyOwed(entry.getValue());
            globalMoneyOwedList.add(dto);
        }

        // 5. Finalize and return the DTO
        userExpensesDto.setMoneyOwedList(globalMoneyOwedList);
        userExpensesDto.setUserGroupResponses(userGroupResponseList);

        return userExpensesDto;
    }

    public List<Split> getExpensesByUserIdAndGroupId(UUID userId, UUID groupId){
        return expenseDao.getExpensesByUserIdAndGroupId(userId, groupId);
    }

    public UserSplitsDto getAllExpensesByUserIdGroupByUserId(UUID userId){

        log.info("All splits for user : {}", userId);

        // Creating Response Object
        UserSplitsDto userSplitsDto = new UserSplitsDto();
        userSplitsDto.setUser(userService.getUserById(userId));
        List<UserSplitHelperDto> userSplitHelperDtoList = new ArrayList<>();
        userSplitsDto.setExpenseList(userSplitHelperDtoList);

        // Get All groups user is part of
        List<Group> allUserGroups = groupDao.getAllGroupsByUserId(userId);
        log.info("All User Group : " + allUserGroups.toString());
        // Get All expenses for each group user is part of
        List<Expense> allUserExpenses = new ArrayList<>();
        for(Group curUserGroup: allUserGroups){
            allUserExpenses.addAll(getExpensesByGroupId(curUserGroup.getGroupId()));
        }
        log.info(allUserExpenses.toString());
        // Get ExpenseId - All expenseIds of the groups user is part of
        Set<UUID> expenseIds = allUserExpenses.stream()
                .map(Expense::getExpenseId)
                .collect(Collectors.toSet());
        log.info(expenseIds.toString());

        // Get All splits for all expensesIds
        List<Split> allUserSplits = splitsDao.getAllSplitsByExpenseIds(expenseIds);

        // Map of UserId - Map of Currency - MoneyOwed
        Map<UUID, Map<Currency, Float>> userMoneyMap = new HashMap<>();

        for (Split userSplit : allUserSplits) {
            if (userSplit == null) continue;

            Expense expense = userSplit.getExpense();
            if (expense == null || expense.getCurrency() == null) continue;

            UUID paidByUserId = expense.getPaidBy().getUserId();
            if (paidByUserId == null) continue;

            UUID splitUserId = userSplit.getUserId(); // or getUserId()
            Float curMoneyOwed = userSplit.getAmountOwed();
            if (curMoneyOwed == null) continue;

            Currency curCurrency = expense.getCurrency();

            // money from targetUser → payer
            if (splitUserId.equals(userId)) {
                Map<Currency, Float> curCurrencyMap =
                        userMoneyMap.computeIfAbsent(paidByUserId, k -> new HashMap<>());
                curCurrencyMap.merge(curCurrency, curMoneyOwed, Float::sum);
            }

            // money from others → targetUser (targetUser is payer)
            if (paidByUserId.equals(userId) && !splitUserId.equals(userId)) {
                Map<Currency, Float> curCurrencyMap =
                        userMoneyMap.computeIfAbsent(splitUserId, k -> new HashMap<>());
                curCurrencyMap.merge(curCurrency, -curMoneyOwed, Float::sum);
            }
        }

        // Remove Current User's data from userMoneyMap
        userMoneyMap.remove(userId);

        // Creating Response
        for (Map.Entry<UUID, Map<Currency, Float>> entry : userMoneyMap.entrySet()) {
            UUID curUserId = entry.getKey();
            UserDto userDto = userService.getUserById(curUserId);
            List<MoneyOwedDto> curMoneyOwedDtos = new ArrayList<>();

            for (Map.Entry<Currency, Float> currencyEntry : entry.getValue().entrySet()) {
                curMoneyOwedDtos.add(new MoneyOwedDto(currencyEntry.getValue(), currencyEntry.getKey()));
            }

            userSplitHelperDtoList.add(new UserSplitHelperDto(userDto, curMoneyOwedDtos));
        }

        return userSplitsDto;
    }

}
