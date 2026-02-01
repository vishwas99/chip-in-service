package com.chipIn.ChipIn.repository;

import com.chipIn.ChipIn.entities.ExpensePayer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpensePayerRepository extends JpaRepository<ExpensePayer, Long> {
}
