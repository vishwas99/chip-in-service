package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dao.UserDao;
import com.chipIn.ChipIn.dto.UserDto;
import com.chipIn.ChipIn.entities.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private UserDao userDao;

    public List<User> getUsers(){
        return userDao.getUsers();
    }

    public UUID addUser(User userData){
        try{
            return userDao.addUser(userData);
        }catch (Exception e){
            throw e;
        }
    }

    public UserDto getUserById(UUID userId){
        User user = userDao.getUserById(userId);
        if(user == null){
            throw new RuntimeException("User not found");
        }
        return new UserDto(user);
    }

}
