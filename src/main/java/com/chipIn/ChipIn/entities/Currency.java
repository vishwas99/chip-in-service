package com.chipIn.ChipIn.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "currencies", schema = "chip_in_core")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Currency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "currency_id")
    private UUID currencyId;

    @Column(nullable = false, unique = true)
    private String code; // e.g., "INR", "USD", "JPY"

    @Column(nullable = false)
    private String name; // e.g., "Indian Rupee", "US Dollar"

    @Column(nullable = false)
    private String symbol; // e.g., "₹", "$", "¥"
}