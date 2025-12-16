package com.chipIn.ChipIn.dto.mapper;

import com.chipIn.ChipIn.dao.CurrencyExchangeDao;
import com.chipIn.ChipIn.dao.GroupDao;
import com.chipIn.ChipIn.dao.UserDao;
import com.chipIn.ChipIn.dto.ExpenseDto;
import com.chipIn.ChipIn.dto.SplitDto;
import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.Split;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class ExpenseMapper {

    private final UserDao userDao;
    private final CurrencyExchangeDao currencyExchangeDao;
    private final GroupDao groupDao;

    @Autowired
    public ExpenseMapper(UserDao userDao, CurrencyExchangeDao currencyExchangeDao, GroupDao groupDao) {
        this.userDao = userDao;
        this.currencyExchangeDao = currencyExchangeDao;
        this.groupDao = groupDao;
    }

    public Expense toEntity(ExpenseDto expenseDto){
        Expense expense = new Expense();
        expense.setName(expenseDto.getExpenseName());
        expense.setPaidBy(expenseDto.getExpenseOwner());
        expense.setAmount(expenseDto.getAmount());
        expense.setDescription(expenseDto.getDescription());
        expense.setGroup(groupDao.getGroupData(expenseDto.getGroupId()));
        expense.setDate(LocalDateTime.now());
        expense.setCurrency(currencyExchangeDao.getCurrency(expenseDto.getCurrencyId()));
        List<Split> splits = new ArrayList<>();
        for (SplitDto splitDto : expenseDto.getExpenseSplit()) {
            Split newSplit = splitDto.toEntity();
            newSplit.setExpense(expense);
            splits.add(newSplit);
        }
        expense.setSplits(splits);
        return expense;
    }


}
