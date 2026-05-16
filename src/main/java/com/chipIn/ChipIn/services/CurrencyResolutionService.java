package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.ExpensePayer;
import com.chipIn.ChipIn.entities.ExpenseSplit;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.GroupCurrency;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import com.chipIn.ChipIn.repository.GroupCurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves expense amounts through the conversion chain:
 *
 *     amount (bucket) --bucket.exchangeRate--> master currency
 *                     --FX row origin=master, master=group.default--> group default
 *                     --FX row origin=group.default, master=user.default--> user default
 *
 * Whenever a step is missing (no FX row), the resolver records the missing
 * pair so the caller can surface it back to the client. Returns `null` (and
 * adds to missing) instead of throwing — so a single broken rate doesn't blow
 * up the entire dashboard.
 *
 * All internal math uses scale 8 with HALF_EVEN. Callers should round to 2 dp
 * at the DTO boundary.
 */
@Service
@RequiredArgsConstructor
public class CurrencyResolutionService {

    private static final int INTERNAL_SCALE = 8;
    private static final RoundingMode INTERNAL_ROUNDING = RoundingMode.HALF_EVEN;

    private final GroupCurrencyRepository groupCurrencyRepository;
    private final CurrencyRepository currencyRepository;

    /**
     * Aggregator that callers reuse across an expense list. Accumulates
     * per-currency, group-default, and (optionally) user-default totals plus
     * a set of missing-rate pairs.
     */
    public static final class Aggregator {
        private final Map<String, BigDecimal> byMaster = new LinkedHashMap<>();
        private BigDecimal totalInGroupDefault = BigDecimal.ZERO;
        private BigDecimal totalInUserDefault = BigDecimal.ZERO;
        private boolean userDefaultResolvable = true;
        private final Map<String, Boolean> missing = new LinkedHashMap<>();

        public Map<String, BigDecimal> getRawByCurrency() {
            Map<String, BigDecimal> out = new LinkedHashMap<>();
            for (Map.Entry<String, BigDecimal> entry : byMaster.entrySet()) {
                out.put(entry.getKey(), entry.getValue().setScale(2, INTERNAL_ROUNDING));
            }
            return out;
        }

        public BigDecimal getTotalInGroupDefault() {
            return totalInGroupDefault.setScale(2, INTERNAL_ROUNDING);
        }

        public BigDecimal getTotalInUserDefault() {
            return userDefaultResolvable ? totalInUserDefault.setScale(2, INTERNAL_ROUNDING) : null;
        }

        public List<String> getMissingRates() {
            return List.copyOf(missing.keySet());
        }
    }

    public Aggregator newAggregator() {
        return new Aggregator();
    }

    /**
     * Records a contribution: the user paid (or owed) `bucketAmount` in
     * `bucket`. Sign is preserved so subtraction (for owed shares) just
     * passes a negative value.
     */
    public void accumulate(Aggregator agg, BigDecimal bucketAmount, GroupCurrency bucket, Group group, User viewer) {
        if (bucketAmount == null || bucket == null || group == null) {
            return;
        }

        // Step 1: bucket -> master
        BigDecimal inMaster = bucketAmount.multiply(bucket.getExchangeRate());
        String masterCode = bucket.getMasterCurrency().getCode();
        agg.byMaster.merge(masterCode, inMaster, BigDecimal::add);

        // Step 2: master -> group default
        Currency groupDefault = group.getDefaultCurrency();
        BigDecimal inGroupDefault = convert(group, bucket.getMasterCurrency(), groupDefault, inMaster, agg);
        if (inGroupDefault == null) {
            // Conversion step failed — leave the master total in rawByCurrency and skip the
            // higher-level totals so they stay accurate.
            agg.userDefaultResolvable = false;
            return;
        }
        agg.totalInGroupDefault = agg.totalInGroupDefault.add(inGroupDefault);

        // Step 3: group default -> user default
        if (viewer == null || viewer.getDefaultCurrencyId() == null) {
            // No viewer or viewer has no default — totalInUserDefault stays unresolved.
            agg.userDefaultResolvable = false;
            return;
        }
        if (groupDefault.getCurrencyId().equals(viewer.getDefaultCurrencyId())) {
            agg.totalInUserDefault = agg.totalInUserDefault.add(inGroupDefault);
            return;
        }
        Optional<Currency> userDefaultOpt = currencyRepository.findById(viewer.getDefaultCurrencyId());
        if (userDefaultOpt.isEmpty()) {
            agg.userDefaultResolvable = false;
            return;
        }
        BigDecimal inUserDefault = convert(group, groupDefault, userDefaultOpt.get(), inGroupDefault, agg);
        if (inUserDefault == null) {
            agg.userDefaultResolvable = false;
            return;
        }
        agg.totalInUserDefault = agg.totalInUserDefault.add(inUserDefault);
    }

    /**
     * One-shot: convert this expense's per-user impact (paidAmount - amountOwed)
     * straight into the group's default currency. Used by the existing dashboard
     * code paths that just want a single number per user.
     *
     * If the conversion can't complete, returns null.
     */
    public BigDecimal expenseImpactInGroupDefault(Expense expense, UUID userId) {
        GroupCurrency bucket = expense.getCurrency();
        Group group = expense.getGroup();
        BigDecimal paid = sumPaidByUser(expense, userId);
        BigDecimal owed = sumOwedByUser(expense, userId);

        BigDecimal netBucket = paid.subtract(owed);
        BigDecimal inMaster = netBucket.multiply(bucket.getExchangeRate());
        return convert(group, bucket.getMasterCurrency(), group.getDefaultCurrency(), inMaster, null);
    }

    /**
     * Same but converts the full expense amount (not user-specific) to the group's default.
     */
    public BigDecimal expenseTotalInGroupDefault(Expense expense) {
        GroupCurrency bucket = expense.getCurrency();
        Group group = expense.getGroup();
        BigDecimal inMaster = expense.getAmount().multiply(bucket.getExchangeRate());
        return convert(group, bucket.getMasterCurrency(), group.getDefaultCurrency(), inMaster, null);
    }

    /**
     * Convert an amount from one true currency to another, looking up an FX
     * row in this group. Returns null (and registers in `agg.missing`, if
     * present) when no rate is available.
     */
    public BigDecimal convert(Group group, Currency from, Currency to, BigDecimal amount, Aggregator agg) {
        if (amount == null) return null;
        if (from == null || to == null) return null;
        if (from.getCurrencyId().equals(to.getCurrencyId())) {
            return amount;
        }
        BigDecimal rate = findRate(group, from, to);
        if (rate == null) {
            if (agg != null) {
                agg.missing.put(from.getCode() + "->" + to.getCode(), Boolean.TRUE);
            }
            return null;
        }
        return amount.multiply(rate).setScale(INTERNAL_SCALE, INTERNAL_ROUNDING);
    }

    /**
     * Find the most recent active FX rate `from -> to` in this group.
     * Returns null when no such row exists.
     */
    public BigDecimal findRate(Group group, Currency from, Currency to) {
        if (from.getCurrencyId().equals(to.getCurrencyId())) {
            return BigDecimal.ONE;
        }
        List<GroupCurrency> direct = groupCurrencyRepository.findFxCandidates(
                group.getGroupId(), from.getCurrencyId(), to.getCurrencyId());
        if (!direct.isEmpty()) {
            return direct.get(0).getExchangeRate();
        }
        // Try the inverse: if we have to -> from at rate r, then from -> to = 1/r.
        List<GroupCurrency> inverse = groupCurrencyRepository.findFxCandidates(
                group.getGroupId(), to.getCurrencyId(), from.getCurrencyId());
        if (!inverse.isEmpty()) {
            BigDecimal r = inverse.get(0).getExchangeRate();
            if (r.signum() == 0) {
                return null;
            }
            return BigDecimal.ONE.divide(r, INTERNAL_SCALE, INTERNAL_ROUNDING);
        }
        return null;
    }

    public Optional<GroupCurrency> findFxRow(Group group, Currency from, Currency to) {
        List<GroupCurrency> candidates = groupCurrencyRepository.findFxCandidates(
                group.getGroupId(), from.getCurrencyId(), to.getCurrencyId());
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
    }

    private BigDecimal sumPaidByUser(Expense expense, UUID userId) {
        if (expense.getPayers() == null) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (ExpensePayer p : expense.getPayers()) {
            if (p.getUser().getUserid().equals(userId)) {
                sum = sum.add(p.getPaidAmount());
            }
        }
        return sum;
    }

    private BigDecimal sumOwedByUser(Expense expense, UUID userId) {
        if (expense.getSplits() == null) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (ExpenseSplit s : expense.getSplits()) {
            if (s.getUser().getUserid().equals(userId)) {
                sum = sum.add(s.getAmountOwed());
            }
        }
        return sum;
    }
}
