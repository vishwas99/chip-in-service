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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid", referencedColumnName = "userid") // change referencedColumnName to your User PK column name
    private User user;

    @Column(name="amountowed")
    private Float amountOwed;

    @ManyToOne
    @JoinColumn(name = "expenseid", nullable = false)
    @JsonBackReference
    @ToString.Exclude
    private Expense expense;

}
