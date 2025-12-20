package com.chipIn.ChipIn.dto.mapper;

import com.chipIn.ChipIn.dao.CurrencyExchangeDao;
import com.chipIn.ChipIn.dao.GroupDao;
import com.chipIn.ChipIn.dao.UserDao;
import com.chipIn.ChipIn.dto.ExpenseDto;
import com.chipIn.ChipIn.dto.SplitDto;
import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.Split;
import com.chipIn.ChipIn.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class ExpenseMapper {

    private final UserDao userDao;
    private final CurrencyExchangeDao currencyExchangeDao;
    private final GroupDao groupDao;
    private final UserService userService;
    private final SplitMapper splitMapper;

    @Autowired
    public ExpenseMapper(UserDao userDao, CurrencyExchangeDao currencyExchangeDao, GroupDao groupDao, UserService userService, SplitMapper splitMapper) {
        this.userDao = userDao;
        this.currencyExchangeDao = currencyExchangeDao;
        this.groupDao = groupDao;
        this.userService = userService;
        this.splitMapper = splitMapper;
    }

    public Expense toEntity(ExpenseDto expenseDto){
        Expense expense = new Expense();
        expense.setName(expenseDto.getExpenseName());
        expense.setPaidBy(userDao.getUserById(expenseDto.getExpenseOwner()));
        expense.setAmount(expenseDto.getAmount());
        expense.setDescription(expenseDto.getDescription());
        expense.setGroup(groupDao.getGroupData(expenseDto.getGroupId()));
        expense.setDate(LocalDateTime.now());
        expense.setCurrency(currencyExchangeDao.getCurrency(expenseDto.getCurrencyId()));
        List<Split> splits = new ArrayList<>();
        for (SplitDto splitDto : expenseDto.getExpenseSplit()) {
            Split newSplit = splitMapper.toEntity(splitDto);
            newSplit.setExpense(expense);
            splits.add(newSplit);
        }
        expense.setSplits(splits);
        return expense;
    }


}
