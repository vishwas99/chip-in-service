package com.chipIn.ChipIn.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "expense_payers", schema = "chip_in_core")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpensePayer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payer_id")
    private UUID payerId;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expenseid", nullable = false)
    private Expense expense;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid", nullable = false)
    private User user;

    // How much this specific user paid (in the custom currency)
    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal paidAmount;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;
}