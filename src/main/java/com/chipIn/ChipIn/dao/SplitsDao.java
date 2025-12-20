package com.chipIn.ChipIn.dao;

import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.Split;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@Transactional
public class SplitsDao {

    @PersistenceContext
    EntityManager entityManager;

    public List<Split> getAllSplitsByUserId(UUID userId){
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Split> criteriaQuery = criteriaBuilder.createQuery(Split.class);
        Root<Split> root = criteriaQuery.from(Split.class);

        criteriaQuery.select(root).where(criteriaBuilder.equal(root.get("userId"), userId));

        return entityManager.createQuery(criteriaQuery).getResultList();

    }

    public List<Split> getAllSplitsByExpenseIds(Set<UUID> expenseIds) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Split> cq = cb.createQuery(Split.class);
        Root<Split> root = cq.from(Split.class);

        // join the Expense relation (assumes Split has a field named "expense")
        Join<Split, ?> expenseJoin = root.join("expense");
        // filter by the expense's id property (use the actual id property name on Expense, e.g. "expenseId" or "id")
        cq.select(root).where(expenseJoin.get("expenseId").in(expenseIds));

        return entityManager.createQuery(cq).getResultList();
    }

    public List<Split> getAllSplitsByExpenseIdsAndUserId(Set<UUID> expenseIds, UUID userId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Split> cq = cb.createQuery(Split.class);
        Root<Split> root = cq.from(Split.class);

        Join<Split, ?> expenseJoin = root.join("expense");

        Predicate byExpenseIds = expenseJoin.get("expenseId").in(expenseIds);
        Predicate byUserId = cb.equal(root.get("userId"), userId);

        cq.select(root).where(cb.and(byExpenseIds, byUserId));

        return entityManager.createQuery(cq).getResultList();
    }

    public List<Split> getSplitsWithUserDataByExpenseId(UUID expenseId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Split> cq = cb.createQuery(Split.class);
        Root<Split> root = cq.from(Split.class);

        // fetch user data
        root.fetch("user", JoinType.LEFT);

        // join expense for filtering
        Join<Split, Expense> expenseJoin = root.join("expense", JoinType.INNER);

        cq.select(root)
                .where(cb.equal(expenseJoin.get("expenseId"), expenseId))
                .distinct(true);

        return entityManager.createQuery(cq).getResultList();
    }

}
