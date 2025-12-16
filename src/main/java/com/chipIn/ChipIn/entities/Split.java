package com.chipIn.ChipIn.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
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
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@EqualsAndHashCode
@ToString
public class Split {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name="splitid", columnDefinition = "uuid")
    @JdbcTypeCode(SqlTypes.UUID)
    @EqualsAndHashCode.Include
    private UUID splitId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name="userid", columnDefinition = "uuid")
    private UUID userId;

    @Column(name="amountowed")
    private Float amountOwed;

    @ManyToOne
    @JoinColumn(name = "expenseid", nullable = false)
    @JsonManagedReference
    @ToString.Exclude
    private Expense expense;

}
