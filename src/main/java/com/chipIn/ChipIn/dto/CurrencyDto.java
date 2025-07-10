package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.dao.CurrencyExchangeDao;
import com.chipIn.ChipIn.dao.UserDao;
import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.services.UserService;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@ToString
public class CurrencyDto {

    private String currencyName;
    private UUID createdBy;
    private String exchangeRate;
    private UUID exchangeTo;


}
