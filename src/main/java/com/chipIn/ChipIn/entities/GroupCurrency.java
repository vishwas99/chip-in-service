package com.chipIn.ChipIn.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(name = "group_currencies", schema = "chip_in_core")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupCurrency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "currency_id")
    private UUID currencyId;

    // Link to the Group
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groupid", nullable = false)
    private Group group;

    // Link to the Global Master Currency (e.g. INR, JPY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_currency_id", nullable = false)
    private Currency masterCurrency;

    // The custom name (e.g., "Base INR", "Airport Yen")
    @Column(nullable = false)
    private String name;

    // The locked-in exchange rate for this specific bucket
    @Column(name = "exchange_rate", precision = 19, scale = 6, nullable = false)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

    // The user who created this rate
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;
}