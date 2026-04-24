package com.chipIn.ChipIn.entities;

import com.chipIn.ChipIn.entities.enums.ExpenseType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "expenses", schema = "chip_in_core")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "expenseid")
    private UUID expenseId;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groupid", nullable = false)
    private Group group;

    @Column(nullable = false)
    private String description;

    // The amount in the CUSTOM currency (e.g., 5000 Yen)
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    // Links to "Airport Yen" to get the exact INR conversion later
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private GroupCurrency currency;


    @Column(name = "split_type", nullable = false)
    private String splitType; // EQUAL, PERCENTAGE, EXACT, SHARES

    @Column(name = "receipt_img_url")
    private String receiptImgUrl;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    @Builder.Default
    private boolean isDeleted = false;

    // Financial Type (EXPENSE vs SETTLEMENT)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ExpenseType type;

    // Visual Category (FOOD, TRAVEL, etc.)
    @Column(name = "category")
    private String category;

    // 1. Link to Payers (Who paid)
    @ToString.Exclude
    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ExpensePayer> payers;

    // 2. Link to Splits (Who owes)
    @ToString.Exclude
    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ExpenseSplit> splits;
}