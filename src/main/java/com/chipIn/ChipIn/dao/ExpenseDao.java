package com.chipIn.ChipIn.dao;

import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.Group;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    public List<Expense> getExpensesByGroupId(UUID groupId){
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Expense> cq = cb.createQuery(Expense.class);
        Root<Expense> root = cq.from(Expense.class);

        root.fetch("splits", JoinType.LEFT);

        cq.select(root).where(cb.equal(root.get("groupId"), groupId)).distinct(true);

        return entityManager.createQuery(cq).getResultList();
    }

    public List<Expense> getExpensesByUserId(UUID userId){
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Expense> criteriaQuery = criteriaBuilder.createQuery(Expense.class);
        Root<Expense> root = criteriaQuery.from(Expense.class);

        root.fetch("splits", JoinType.LEFT);

        criteriaQuery.select(root).where(criteriaBuilder.equal(root.get("userId"), userId));
        return entityManager.createQuery(criteriaQuery).getResultList();
    }


}
