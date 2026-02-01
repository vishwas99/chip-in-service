package com.chipIn.ChipIn.repository;

import com.chipIn.ChipIn.entities.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CurrencyRepository extends JpaRepository<Currency, UUID> {
    Optional<Currency> findById(UUID currencyId);
}
