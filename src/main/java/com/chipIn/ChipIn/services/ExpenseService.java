package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dao.CurrencyExchangeDao;
import com.chipIn.ChipIn.dao.ExpenseDao;
import com.chipIn.ChipIn.dao.SplitsDao;
import com.chipIn.ChipIn.dao.UserToGroupDao;
import com.chipIn.ChipIn.dto.*;
import com.chipIn.ChipIn.entities.*;
import com.chipIn.ChipIn.entities.Currency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

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
                splitDto.setAmount(splitDto.getAmount() * totalAmount * -1);
            }else{
                splitDto.setAmount(totalAmount - splitDto.getAmount() * totalAmount);
            }
        }

        log.info("Expense List : {}", expenseDto.getExpenseSplit());

        // 3. Record expense in the group
        expenseDao.addExpenseToGroup(expenseDto.toEntity());

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
            List<GroupExpenseDto> groupExpenseList = new ArrayList<>();
            for (Map.Entry<String, Float> entry : currentGroupMap.entrySet()) {
                GroupExpenseDto dto = new GroupExpenseDto();
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
        List<GroupExpenseDto> globalMoneyOwedList = new ArrayList<>();
        for (Map.Entry<String, Float> entry : globalCurrencyMap.entrySet()) {
            GroupExpenseDto dto = new GroupExpenseDto();
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



}
