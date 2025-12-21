package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dao.SplitsDao;
import com.chipIn.ChipIn.dao.UserDao;
import com.chipIn.ChipIn.dao.UserToGroupDao;
import com.chipIn.ChipIn.dto.UserDto;
import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.Split;
import com.chipIn.ChipIn.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserDao userDao;

    @Autowired
    private SplitsDao splitsDao;

    @Autowired
    private UserToGroupDao userToGroupDao;

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

    public List<UserDto> getUsersByUserIds(List<UUID> userIds){
        return userDao.getUsersByIds(userIds).stream()
                .map(UserDto::new)
                .toList();
    }

    public Set<User> getKnownUsers(UUID userId){
        // Get all users who current user shared expenses with
        // Get all Splits for current User
        List<Split> userSplits = splitsDao.getAllSplitsByUserId(userId);
        log.info(userSplits.toString());
        // Get Expense for the Split
        Set<UUID> allExpenses = userSplits.stream().map(Split::getExpense).map(Expense::getExpenseId).collect(Collectors.toSet());

        // Get all Splits for all Given Expense Ids
        List<Split> allSplits = splitsDao.getAllSplitsByExpenseIds(allExpenses);

        // Get all user for all Splits for all the expenses
        Set<User> knownUsers = allSplits.stream().map(Split::getUser).collect(Collectors.toSet());

        knownUsers.remove(new User(userId));

        return knownUsers;
    }

    public Set<User> getKnownUsersByGroup(UUID userId, UUID groupId){
        Set<User> knownUsers = getKnownUsers(userId);
        List<User> usersInGroup = userToGroupDao.getUsersInGroup(groupId);
        // 1. Create a Set of IDs to exclude (Lookup is O(1))
        Set<UUID> existingUserIds = usersInGroup.stream()
                        .map(User::getUserId)
                        .collect(Collectors.toSet());

        // 2. Filter using IDs (Safe even if equals() is broken)
        return knownUsers.stream()
                        .filter(user -> !existingUserIds.contains(user.getUserId()))
                        .collect(Collectors.toSet());
    }

    public User getUserByEmail(String email){
        Optional<User> user = userDao.getUserByEmail(email);
        if(user.isEmpty()){
            throw new RuntimeException("Invalid Email, No such user exist");
        }
        return user.get();
    }

}
