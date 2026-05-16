package com.chipIn.ChipIn.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A "currency bucket" inside a single group. Plays one of two roles:
 *
 *  1. Expense bucket (the common case). Referenced by Expense.currency.
 *     - originCurrency is the currency that the bucket is denominated in
 *       (e.g. a custom YEN-Day1 bucket has originCurrency = null or the
 *       master JPY; the auto-created "Base INR" row has originCurrency = INR).
 *     - exchangeRate converts 1 unit of the bucket into 1 unit of masterCurrency.
 *     - amounts on Expense / ExpensePayer / ExpenseSplit are in the bucket's unit.
 *
 *  2. FX rate row (origin != master). Never referenced by an Expense — only
 *     read by CurrencyResolutionService to convert a master-currency amount
 *     into the group's default currency (or into the user's default currency).
 *     A daily job (out-of-scope here) is expected to UPSERT these rows.
 *
 * Look-up convention: for the second hop, the resolver searches for a row
 * in this group where origin == bucket.master and master == group.default.
 */
@Entity
@Table(name = "group_currencies", schema = "chip_in_core")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"group", "masterCurrency", "originCurrency", "createdBy"})
public class GroupCurrency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "currency_id")
    private UUID currencyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groupid", nullable = false)
    private Group group;

    /** What currency this bucket is denominated in. Null is allowed for legacy
     *  rows; new rows should always set this. For FX rate rows this is the
     *  "from" currency. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_currency_id")
    private Currency originCurrency;

    /** The currency the exchangeRate converts INTO. For a bucket this is the
     *  underlying true currency (e.g. JPY). For an FX rate row this is the
     *  "to" currency (typically the group's default currency). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_currency_id", nullable = false)
    private Currency masterCurrency;

    /** Display name. For buckets this is what the user sees ("Base INR",
     *  "YEN-Day1"). For FX rate rows the resolver doesn't care, but we still
     *  store something readable like "FX JPY->INR". */
    @Column(nullable = false)
    private String name;

    /** 1 unit of originCurrency (or the bucket-unit if origin is null) equals
     *  exchangeRate units of masterCurrency. Precision allows for very small
     *  rates (e.g. IDR -> USD ~= 0.0000635). */
    @Column(name = "exchange_rate", precision = 19, scale = 6, nullable = false)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;
}
