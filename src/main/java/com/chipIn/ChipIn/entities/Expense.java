package com.chipIn.ChipIn.entities;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "expenseid", columnDefinition = "uuid")
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID expenseId;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "amount")
    private double amount;

    @Column(name = "created_at")
    private LocalDateTime date;

    @Column(name="paidby")
    private UUID paidBy;

    @Column(name="groupid")
    private UUID groupId;

    @Column(name = "currency_id")
    private UUID currencyId;

    @OneToMany(
            mappedBy = "expense",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true
    )
    @JsonManagedReference
    private List<Split> splits;
}
