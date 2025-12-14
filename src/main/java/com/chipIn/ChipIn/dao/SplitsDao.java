package com.chipIn.ChipIn.dao;

import com.chipIn.ChipIn.entities.Split;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

        System.out.println("Query Created : " + root.get("userId").getJavaType());
        criteriaQuery.select(root).where(criteriaBuilder.equal(root.get("userId"), userId));

        return entityManager.createQuery(criteriaQuery).getResultList();

    }

}
