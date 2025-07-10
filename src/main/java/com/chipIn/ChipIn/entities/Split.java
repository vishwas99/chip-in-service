package com.chipIn.ChipIn.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "expense_splits")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Split {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name="splitid")
    private UUID splitId;

    @Column(name="userid")
    private UUID userId;

    @Column(name="amountowed")
    private Double amountOwed;

    @ManyToOne
    @JoinColumn(name = "expenseid", nullable = false)
    private Expense expense;

}
