package com.chipIn.ChipIn.dao;

import com.chipIn.ChipIn.entities.Split;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
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

}
