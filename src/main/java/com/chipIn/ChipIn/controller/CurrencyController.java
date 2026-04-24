package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.services.CurrencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/currencies")
@RequiredArgsConstructor
public class CurrencyController extends BaseController {

    private final CurrencyService currencyService;

    // We pass the groupId as an optional request param to get relevant currencies
    @GetMapping("")
    public ResponseEntity<List<Currency>> getCurrencies(@RequestParam(required = false, name = "groupId") UUID groupId) {
        return ResponseEntity.ok(currencyService.getCurrenciesForGroup(groupId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Currency> getCurrencyById(@PathVariable("id") UUID id) {
        return currencyService.getCurrencyById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("")
    public ResponseEntity<Currency> createCurrency(@Valid @RequestBody Currency currency) {
        Currency createdCurrency = currencyService.createCurrency(currency);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCurrency);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCurrency(@PathVariable("id") UUID id) {
        currencyService.deleteCurrency(id);
        return ResponseEntity.noContent().build();
    }
}
