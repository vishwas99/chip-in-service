package com.chipIn.ChipIn.entities;

import com.chipIn.ChipIn.dto.CurrencyDto;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "currency_exchange")
public class Currency {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "currency_name", nullable = false)
    private String currencyName;

    @Column(name = "exchange_rate")
    private Float exchangeRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_to")
    @ToString.Exclude
    private Currency exchangeTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    @ToString.Exclude
    private User createdBy;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;
}
