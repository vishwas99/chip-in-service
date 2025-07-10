package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dao.ExpenseDao;
import com.chipIn.ChipIn.dao.UserToGroupDao;
import com.chipIn.ChipIn.dto.ExpenseDto;
import com.chipIn.ChipIn.dto.SplitDto;
import com.chipIn.ChipIn.dto.UserGroupResponse;
import com.chipIn.ChipIn.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class ExpenseService {

    @Autowired
    private GroupService groupService;

    @Autowired
    private ExpenseDao expenseDao;

    @Autowired
    private UserToGroupDao userToGroupDao;

    public void addExpense(ExpenseDto expenseDto){
        // Logic to add expense to the group

        // 1. Validate all users in split
        Set<UUID> groupMembers = groupService.getUserByGroupId(expenseDto.getGroupId());

        // 2. Calculate exact amount split for each user
        Double totalAmount = expenseDto.getAmount();
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

}
