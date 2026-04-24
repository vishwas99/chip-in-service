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
@Table(name = "expense_splits", schema = "chip_in_core")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "splitid")
    private UUID splitId;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expenseid", nullable = false)
    private Expense expense;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid", nullable = false)
    private User user;

    // The calculated amount this user owes based on the split type
    @Column(name = "amount_owed", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountOwed;

    // Used for non-EQUAL splits (e.g., 20%, 3 shares, etc.)
    @Column(name = "raw_value", precision = 19, scale = 4)
    private BigDecimal rawValue;
}