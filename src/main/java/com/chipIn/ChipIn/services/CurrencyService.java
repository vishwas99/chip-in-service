package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.GroupCurrency;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import com.chipIn.ChipIn.repository.GroupCurrencyRepository;
import com.chipIn.ChipIn.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CurrencyService {

    private final CurrencyRepository currencyRepository;
    private final GroupRepository groupRepository;
    private final GroupCurrencyRepository groupCurrencyRepository;

    public List<Currency> getCurrenciesForGroup(UUID groupId) {
        return currencyRepository.findActiveCurrenciesForGroupOrGlobal(groupId);
    }

    public Optional<Currency> getCurrencyById(UUID currencyId) {
        return currencyRepository.findById(currencyId)
                .filter(Currency::isActive);
    }

    /**
     * Create a global currency (group must be null). Group-scoped Currency rows
     * are no longer supported — use GroupCurrency for per-group buckets.
     */
    public Currency createCurrency(Currency currency) {
        if (currency.getGroup() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Group-scoped currencies have been replaced by group_currencies. " +
                            "Use POST /api/groups/{groupId}/currencies instead.");
        }
        currency.setGroup(null);
        return currencyRepository.save(currency);
    }

    /**
     * Resolves the currency identified by `currencyId` for an expense in `groupId`,
     * always returning a `GroupCurrency` bucket the expense can point at.
     *
     * Accepts:
     *   - A GroupCurrency id (must belong to this group, must be active).
     *   - A global Currency id — the resolver auto-creates a default bucket
     *     in this group with origin = master = the global currency and rate = 1.
     *
     * Throws 400 for any inconsistency.
     */
    public GroupCurrency validateAndGetGroupCurrency(UUID groupId, UUID currencyId, User currentUser) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        Optional<GroupCurrency> groupCurrencyOpt = groupCurrencyRepository.findById(currencyId);
        if (groupCurrencyOpt.isPresent()) {
            GroupCurrency groupCurrency = groupCurrencyOpt.get();
            if (!groupCurrency.getGroup().getGroupId().equals(groupId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency does not belong to this group");
            }
            if (!groupCurrency.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency bucket is not active");
            }
            // Reject FX rate rows (origin != master) — they're not valid expense buckets.
            if (groupCurrency.getOriginCurrency() != null
                    && !groupCurrency.getOriginCurrency().getCurrencyId().equals(groupCurrency.getMasterCurrency().getCurrencyId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This entry is an FX rate row and cannot be used as an expense currency");
            }
            return groupCurrency;
        }

        Currency globalCurrency = currencyRepository.findById(currencyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency not found"));

        if (!globalCurrency.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency is not active");
        }
        if (globalCurrency.getGroup() != null && !globalCurrency.getGroup().getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This custom currency does not belong to this group");
        }

        return groupCurrencyRepository.findByGroupAndMasterCurrency(group, globalCurrency)
                .orElseGet(() -> {
                    GroupCurrency newGroupCurrency = GroupCurrency.builder()
                            .group(group)
                            .originCurrency(globalCurrency)
                            .masterCurrency(globalCurrency)
                            .name(globalCurrency.getName())
                            .exchangeRate(BigDecimal.ONE)
                            .createdBy(currentUser)
                            .isActive(true)
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
