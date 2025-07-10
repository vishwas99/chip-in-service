package com.chipIn.ChipIn.dto.mapper;

import com.chipIn.ChipIn.dao.CurrencyExchangeDao;
import com.chipIn.ChipIn.dao.UserDao;
import com.chipIn.ChipIn.dto.CurrencyDto;
import com.chipIn.ChipIn.entities.Currency;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class CurrencyMapper {

    private final UserDao userDao;
    private final CurrencyExchangeDao currencyExchangeDao;

    @Autowired
    public CurrencyMapper(UserDao userDao, CurrencyExchangeDao currencyExchangeDao) {
        this.userDao = userDao;
        this.currencyExchangeDao = currencyExchangeDao;
    }

    public Currency toEntity(CurrencyDto dto) {
        Currency entity = new Currency();
        entity.setCurrencyName(dto.getCurrencyName());
        if(dto.getExchangeRate() != null){
            entity.setExchangeRate(Float.parseFloat(dto.getExchangeRate()));
        }
        entity.setCreatedOn(OffsetDateTime.now().toLocalDateTime());

        entity.setCreatedBy(userDao.getUserById(dto.getCreatedBy()));

        if (dto.getExchangeTo() != null) {
            entity.setExchangeTo(currencyExchangeDao.getCurrency(dto.getExchangeTo()));
        }

        return entity;
    }
}
