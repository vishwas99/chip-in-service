package com.chipIn.ChipIn.repository;

import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.GroupCurrency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupCurrencyRepository extends JpaRepository<GroupCurrency, UUID> {

    Optional<GroupCurrency> findByGroupAndMasterCurrency(Group group, Currency masterCurrency);

    /**
     * Lookup used by the resolver: the most recent active FX row in this group
     * that maps amounts in `from` into amounts in `to`. Buckets where origin == master
     * also satisfy this when from == to == that currency.
     */
    @Query("""
            SELECT gc FROM GroupCurrency gc
            WHERE gc.group.groupId = :groupId
              AND gc.originCurrency.currencyId = :fromCurrencyId
              AND gc.masterCurrency.currencyId = :toCurrencyId
              AND gc.isActive = true
            ORDER BY gc.createdAt DESC
            """)
    List<GroupCurrency> findFxCandidates(@Param("groupId") UUID groupId,
                                        @Param("fromCurrencyId") UUID fromCurrencyId,
                                        @Param("toCurrencyId") UUID toCurrencyId);

    /** List every active bucket / FX row in a group — used by GET /groups/{id}/currencies. */
    @Query("""
            SELECT gc FROM GroupCurrency gc
            WHERE gc.group.groupId = :groupId
              AND gc.isActive = true
            ORDER BY gc.createdAt ASC
            """)
    List<GroupCurrency> findActiveByGroupId(@Param("groupId") UUID groupId);

    /** True when at least one non-deleted expense in this group references the bucket. */
    @Query("""
            SELECT COUNT(e) > 0 FROM Expense e
            WHERE e.currency.currencyId = :groupCurrencyId
              AND e.isDeleted = false
            """)
    boolean isReferencedByExpense(@Param("groupCurrencyId") UUID groupCurrencyId);
}
