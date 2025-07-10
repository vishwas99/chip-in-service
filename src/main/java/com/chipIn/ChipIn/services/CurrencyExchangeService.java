package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dao.CurrencyExchangeDao;
import com.chipIn.ChipIn.dto.CurrencyDto;
import com.chipIn.ChipIn.dto.mapper.CurrencyMapper;
import com.chipIn.ChipIn.entities.Currency;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrencyExchangeService {

    @Autowired
    private CurrencyExchangeDao currencyExchangeDao;

    @Autowired
    private CurrencyMapper currencyMapper;



    public CurrencyDto getCurrencyById(UUID currencyId){

        Currency currency = currencyExchangeDao.getCurrency(currencyId);
        CurrencyDto currencyDto = new CurrencyDto();
        currencyDto.setCurrencyName(currency.getCurrencyName());
        if(currency.getExchangeTo() != null){
            currencyDto.setExchangeTo(currency.getExchangeTo().getId());
        }
        currencyDto.setCreatedBy(currency.getCreatedBy().getUserId());
        if(currency.getExchangeRate() != null){
            currencyDto.setExchangeRate(String.valueOf(currency.getExchangeRate()));
        }
        return currencyDto;
    }

    public Currency createCurrency(CurrencyDto currencyDto){

        return currencyExchangeDao.createCurrency(currencyMapper.toEntity(currencyDto));

    }

}
