package com.chipIn.ChipIn.repository;


import com.chipIn.ChipIn.entities.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    Optional<Expense> findById(UUID expenseId);

    List<Expense> findByGroupGroupIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID groupId);
}
