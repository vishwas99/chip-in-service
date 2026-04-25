package com.chipIn.ChipIn.repository;

import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.GroupCurrency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GroupCurrencyRepository extends JpaRepository<GroupCurrency, UUID> {
    Optional<GroupCurrency> findByGroupAndMasterCurrency(Group group, Currency masterCurrency);
}
