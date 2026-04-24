package com.chipIn.ChipIn.repository;

import com.chipIn.ChipIn.entities.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CurrencyRepository extends JpaRepository<Currency, UUID> {
    Optional<Currency> findById(UUID currencyId);

    Optional<Currency> findByCode(String code);

    @Query("SELECT c FROM Currency c WHERE c.isActive = true AND (c.group IS NULL OR c.group.groupId = :groupId)")
    List<Currency> findActiveCurrenciesForGroupOrGlobal(@Param("groupId") UUID groupId);
}
