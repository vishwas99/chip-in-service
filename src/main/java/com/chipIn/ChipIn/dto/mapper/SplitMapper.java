package com.chipIn.ChipIn.dto.mapper;

import com.chipIn.ChipIn.dao.UserDao;
import com.chipIn.ChipIn.dto.SplitDto;
import com.chipIn.ChipIn.entities.Split;
import org.springframework.stereotype.Component;

@Component
public class SplitMapper {

    // Generate Mapper to map Split entity to SplitDto and vice versa
    private final UserDao userDao;
    public SplitMapper(UserDao userDao) {
         this.userDao = userDao;
    }

    public SplitDto toDto(Split split) {
        SplitDto splitDto = new SplitDto();
        splitDto.setUserId(split.getUser().getUserId());
        splitDto.setAmount(split.getAmountOwed());
        return splitDto;
    }

    public Split toEntity(SplitDto splitDto) {
        Split split = new Split();
        split.setUser(userDao.getUserById(splitDto.getUserId()));
        split.setAmountOwed(splitDto.getAmount());
        return split;
    }

}
