package com.chipIn.ChipIn.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "currencies", schema = "chip_in_core")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Currency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "currency_id")
    private UUID currencyId;

    @EqualsAndHashCode.Include
    @NotBlank(message = "Currency code cannot be blank")
    @Size(min = 3, max = 3, message = "Currency code must be 3 characters long (ISO 4217)")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency code must consist of 3 uppercase letters")
    @Column(nullable = false, unique = true)
    private String code; // e.g., "INR", "USD", "JPY"

    @NotBlank(message = "Currency name cannot be blank")
    @Size(max = 100, message = "Currency name cannot exceed 100 characters")
    @Column(nullable = false)
    private String name; // e.g., "Indian Rupee", "US Dollar"

    @NotBlank(message = "Currency symbol cannot be blank")
    @Size(max = 10, message = "Currency symbol cannot exceed 10 characters")
    @Column(nullable = false)
    private String symbol; // e.g., "₹", "$", "¥"
}