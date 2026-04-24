package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import com.chipIn.ChipIn.repository.GroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CurrencyService {

    private final CurrencyRepository currencyRepository;
    private final GroupRepository groupRepository;

    @Autowired
    public CurrencyService(CurrencyRepository currencyRepository, GroupRepository groupRepository) {
        this.currencyRepository = currencyRepository;
        this.groupRepository = groupRepository;
    }

    public List<Currency> getCurrenciesForGroup(UUID groupId) {
        return currencyRepository.findActiveCurrenciesForGroupOrGlobal(groupId);
    }

    public Optional<Currency> getCurrencyById(UUID currencyId) {
        return currencyRepository.findById(currencyId)
                .filter(Currency::isActive);
    }

    public Currency createCurrency(Currency currency) {
        // Verify if it's a valid group if a group is provided (custom currency)
        if (currency.getGroup() != null && currency.getGroup().getGroupId() != null) {
            Group group = groupRepository.findById(currency.getGroup().getGroupId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid Group ID: Group does not exist"));
            currency.setGroup(group);
        } else {
            currency.setGroup(null); // Explicitly ensure it's null for global currencies
        }
        return currencyRepository.save(currency);
    }
    
    public void deleteCurrency(UUID currencyId) {
        currencyRepository.findById(currencyId).ifPresent(currency -> {
            currency.setActive(false);
            currencyRepository.save(currency);
        });
    }
}
