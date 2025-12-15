package com.chipIn.ChipIn.dao;

import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.Group; // Ensure this is imported
import com.chipIn.ChipIn.entities.Split;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
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

        // FIX 1: "groupId" field no longer exists in Expense. It is now "group".
        // We join to the group to filter by its ID.
        Join<Expense, Group> groupJoin = root.join("group", JoinType.INNER);

        // Assuming Group entity has a field "groupId". If it's "id", change below to "id".
        cq.select(root)
                .where(cb.equal(groupJoin.get("groupId"), groupId))
                .distinct(true);

        return entityManager.createQuery(cq).getResultList();
    }

    public List<Expense> getExpensesByUserId(UUID userId){
        CriteriaBuilder cb = entityManager.getCriteriaBuilder(); // Renamed for consistency
        CriteriaQuery<Expense> cq = cb.createQuery(Expense.class);
        Root<Expense> root = cq.from(Expense.class);

        root.fetch("splits", JoinType.LEFT);

        // FIX 2: Expense entity has "paidBy", not "userId".
        cq.select(root).where(cb.equal(root.get("paidBy"), userId));

        return entityManager.createQuery(cq).getResultList();
    }

    public List<Split> getExpensesByUserIdAndGroupId(UUID userId, UUID groupId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Split> cq = cb.createQuery(Split.class);

        Root<Split> split = cq.from(Split.class);

        // Fetch expense and currency
        Fetch<Split, Expense> expenseFetch = split.fetch("expense", JoinType.INNER);
        expenseFetch.fetch("currency", JoinType.LEFT);

        // Cast to Join for filtering
        Join<Split, Expense> expenseJoin = (Join<Split, Expense>) expenseFetch;

        // FIX 3: Ensure we join on "group" (the entity field name), not "groupId"
        Join<Expense, Group> groupJoin = expenseJoin.join("group", JoinType.INNER);

        cq.select(split)
                .where(
                        cb.and(
                                cb.equal(split.get("userId"), userId),
                                cb.equal(groupJoin.get("groupId"), groupId)
                        )
                );

        return entityManager.createQuery(cq).getResultList();
    }
}