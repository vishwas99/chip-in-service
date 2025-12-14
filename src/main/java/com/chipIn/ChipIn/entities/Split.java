package com.chipIn.ChipIn.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @Column(name="splitid", columnDefinition = "uuid")
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID splitId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name="userid", columnDefinition = "uuid")
    private UUID userId;

    @Column(name="amountowed")
    private Float amountOwed;

    @ManyToOne
    @JoinColumn(name = "expenseid", nullable = false)
    @JsonBackReference
    private Expense expense;

}
