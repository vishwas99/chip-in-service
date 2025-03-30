package com.chipIn.ChipIn.dao;

import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.Group;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@Transactional
public class ExpenseDao {

    @PersistenceContext
    EntityManager entityManager;

    @Transactional
    public void addExpenseToGroup(Expense expense){
        entityManager.persist(expense);
        entityManager.flush();
    }

}
