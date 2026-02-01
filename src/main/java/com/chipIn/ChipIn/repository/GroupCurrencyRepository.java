package com.chipIn.ChipIn.repository;

import com.chipIn.ChipIn.entities.GroupCurrency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GroupCurrencyRepository extends JpaRepository<GroupCurrency, UUID> {
}
