package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dao.UserDao;
import com.chipIn.ChipIn.dto.UserDto;
import com.chipIn.ChipIn.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserDao userDao;

    public List<User> getUsers(){
        return userDao.getUsers();
    }

    public UUID addUser(User userData){
        try{
            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
            String hashedPassword = passwordEncoder.encode(userData.getPassword());
            userData.setPassword(hashedPassword);
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

    public org.springframework.security.core.userdetails.User loadUserByUsername(String email) throws UsernameNotFoundException {
        System.out.println("Fetching user by email: " + email);

        Optional<User> userOpt = userDao.getUserByEmail(email);

        System.out.println("User found: " + userOpt.isPresent());

        if (userOpt.isEmpty()) {
            throw new UsernameNotFoundException("User not found: " + email);
        }

        User user = userOpt.get();
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Collections.emptyList()
        );
    }

}
