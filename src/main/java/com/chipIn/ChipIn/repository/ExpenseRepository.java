package com.chipIn.ChipIn.repository;


import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    Optional<Expense> findById(UUID expenseId);

    List<Expense> findByGroupGroupIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID groupId);

    List<Expense> findAllByGroupIn(List<Group> groups);

    List<Expense> findAllByGroupInAndIsDeletedFalse(List<Group> groups);

}
