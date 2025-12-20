package com.chipIn.ChipIn.dao;

import com.chipIn.ChipIn.controller.CurrencyController;
import com.chipIn.ChipIn.dto.CurrencyDto;
import com.chipIn.ChipIn.entities.Currency;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@Transactional
public class CurrencyExchangeDao{

    @PersistenceContext
    EntityManager entityManager;

    @Transactional
    public Currency getCurrency(UUID currencyId){
        return entityManager.find(Currency.class, currencyId);
    }

    @Transactional
    public Currency createCurrency(Currency currency){
        entityManager.persist(currency);
        entityManager.flush();

        return entityManager.find(Currency.class, currency.getId());
    }

    public List<Currency> getAllCurrencies(UUID userId){
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Currency> cq = cb.createQuery(Currency.class);
        Root<Currency> root = cq.from(Currency.class);

        Predicate byUser = cb.equal(root.get("createdBy").get("userId"), userId);
        Predicate global = cb.isNull(root.get("createdBy"));

        cq.where(cb.or(byUser, global));

        return  entityManager.createQuery(cq).getResultList();

    }
}
