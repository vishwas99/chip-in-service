package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.CurrencyDto;
import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.services.CurrencyExchangeService;
import com.chipIn.ChipIn.util.ResponseWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/currency")
@Validated
@Slf4j
public class CurrencyController extends BaseController {

    @Autowired
    CurrencyExchangeService currencyExchangeService;

    @GetMapping("/getCurrencyById")
    public ResponseEntity<ResponseWrapper<CurrencyDto>> getCurrencyById(@RequestParam("id") UUID id){
        return ResponseEntity.ok(ResponseWrapper.success(currencyExchangeService.getCurrencyById(id)));
    }

    @PostMapping("/createCurrency")
    public ResponseEntity<ResponseWrapper<Currency>> createCurrency(@RequestBody CurrencyDto currencyDto){
        return ResponseEntity.ok(ResponseWrapper.success(currencyExchangeService.createCurrency(currencyDto)));
    }

}
