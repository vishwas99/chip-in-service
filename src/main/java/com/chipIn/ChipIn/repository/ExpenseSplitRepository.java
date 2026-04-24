package com.chipIn.ChipIn.repository;

import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Long> {

    List<ExpenseSplit> findByExpenseExpenseIdIn(List<Long> expenseIds);

    List<ExpenseSplit> findByExpenseIn(List<Expense> expenses);

}
