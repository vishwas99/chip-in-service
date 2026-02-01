package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/currencies")
@RequiredArgsConstructor
public class CurrencyController extends  BaseController{

    private  final CurrencyRepository currencyRepository;

    @GetMapping("")
    public List<Currency> getCurrencies(){
        return currencyRepository.findAll();
    }
}
