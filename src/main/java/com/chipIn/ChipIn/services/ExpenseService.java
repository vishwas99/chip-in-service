package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dao.CurrencyExchangeDao;
import com.chipIn.ChipIn.dao.ExpenseDao;
import com.chipIn.ChipIn.dao.SplitsDao;
import com.chipIn.ChipIn.dao.UserToGroupDao;
import com.chipIn.ChipIn.dto.ExpenseDto;
import com.chipIn.ChipIn.dto.SplitDto;
import com.chipIn.ChipIn.dto.UserGroupResponse;
import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.Split;
import com.chipIn.ChipIn.entities.User;
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

    public List<Split> getExpensesByUserId(UUID userId){
        // Get All expense Splits for the UserId groupBy groupId


        // From Splits get money value of each Split from ExpenseTable

        // Tally and Return Response

        return splitsDao.getAllSplitsByUserId(userId);
    }
}
