package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.GroupCurrency;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import com.chipIn.ChipIn.repository.GroupCurrencyRepository;
import com.chipIn.ChipIn.repository.GroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CurrencyService {

    private final CurrencyRepository currencyRepository;
    private final GroupRepository groupRepository;
    private final GroupCurrencyRepository groupCurrencyRepository;

    @Autowired
    public CurrencyService(CurrencyRepository currencyRepository, GroupRepository groupRepository,
                           GroupCurrencyRepository groupCurrencyRepository) {
        this.currencyRepository = currencyRepository;
        this.groupRepository = groupRepository;
        this.groupCurrencyRepository = groupCurrencyRepository;
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

    /**
     * Global method to validate and get a GroupCurrency for a group.
     * This handles both:
     * - Currencies in the group_currencies table (custom group currencies)
     * - Global currencies in the currencies table (global currencies used by a group)
     *
     * The method ensures that:
     * 1. The currency exists (either as GroupCurrency or global Currency)
     * 2. The currency is active
     * 3. If it's a global currency, it's not specific to another group
     * 4. If it's a global currency, a corresponding GroupCurrency is created/fetched
     *
     * @param groupId the ID of the group
     * @param currencyId the ID of the currency (could be from GroupCurrency or Currency table)
     * @param currentUser the user making the request (needed if creating a new GroupCurrency)
     * @return the validated GroupCurrency
     * @throws RuntimeException if the group is not found
     * @throws IllegalArgumentException if the currency is not found or not valid for this group
     */
    public GroupCurrency validateAndGetGroupCurrency(UUID groupId, UUID currencyId, User currentUser) {
        // Fetch the group first
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        // Step 1: Try to fetch from GroupCurrency table first
        Optional<GroupCurrency> groupCurrencyOpt = groupCurrencyRepository.findById(currencyId);
        if (groupCurrencyOpt.isPresent()) {
            GroupCurrency groupCurrency = groupCurrencyOpt.get();

            // Validate that this GroupCurrency belongs to the requested group
            if (!groupCurrency.getGroup().getGroupId().equals(groupId)) {
                throw new IllegalArgumentException("Currency does not belong to this group");
            }

            return groupCurrency;
        }

        // Step 2: Try to fetch from global Currency table
        Optional<Currency> globalCurrencyOpt = currencyRepository.findById(currencyId);
        if (globalCurrencyOpt.isEmpty()) {
            throw new IllegalArgumentException("Currency not found");
        }

        Currency globalCurrency = globalCurrencyOpt.get();

        // Validate the global currency
        if (!globalCurrency.isActive()) {
            throw new IllegalArgumentException("Currency is not active");
        }

        // If currency has a group (custom currency for another group), reject it
        if (globalCurrency.getGroup() != null && !globalCurrency.getGroup().getGroupId().equals(groupId)) {
            throw new IllegalArgumentException("This custom currency does not belong to this group");
        }

        // Step 3: Find or create the GroupCurrency mapping for this group and global currency
        return groupCurrencyRepository.findByGroupAndMasterCurrency(group, globalCurrency)
                .orElseGet(() -> {
                    // Create a new GroupCurrency mapping if it doesn't exist
                    GroupCurrency newGroupCurrency = GroupCurrency.builder()
                            .group(group)
                            .masterCurrency(globalCurrency)
                            .name(globalCurrency.getName())
                            .exchangeRate(BigDecimal.ONE) // Default exchange rate
                            .createdBy(currentUser)
                            .build();
                    return groupCurrencyRepository.save(newGroupCurrency);
                });
    }

    public void deleteCurrency(UUID currencyId) {
        currencyRepository.findById(currencyId).ifPresent(currency -> {
            currency.setActive(false);
            currencyRepository.save(currency);
        });
    }
}
