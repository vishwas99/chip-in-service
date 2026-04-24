package com.chipIn.ChipIn.services;


import com.chipIn.ChipIn.dto.LoginRequest;
import com.chipIn.ChipIn.dto.SignupRequest;
import com.chipIn.ChipIn.dto.UpdateProfileRequest;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.entities.enums.AuthProvider;
import com.chipIn.ChipIn.entities.enums.UserStatus;
import com.chipIn.ChipIn.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User registerUser(SignupRequest signupRequest){
        if(userRepository.existsByEmail(signupRequest.getEmail())){
            throw new RuntimeException("User already exists");
        }

        User user = User.builder().name(signupRequest.getName())
                .email(signupRequest.getEmail())
                .password(passwordEncoder.encode(signupRequest.getPassword()))
                .name(signupRequest.getName())
                .phone(signupRequest.getPhone())
                .authProvider(AuthProvider.LOCAL)
                .status(UserStatus.ACTIVE)
                .isRegistered(true)
                .build();

        return userRepository.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return null;
    }

    public User getUserByEmail(String email){
        Optional<User> user = userRepository.findByEmail(email);
        return user.orElse(null);
    }

    public void disableUser(String email){
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new RuntimeException("User not found")
        );
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);
    }

    public void enableUser(String email){
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new RuntimeException("User not found")
        );
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    public User updateProfile(User currentUser, UpdateProfileRequest request) {
        if (request.getName() != null && !request.getName().isEmpty()) {
            currentUser.setName(request.getName());
        }
        if (request.getPhone() != null && !request.getPhone().isEmpty()) {
            currentUser.setPhone(request.getPhone());
        }
        if (request.getProfilePicUrl() != null && !request.getProfilePicUrl().isEmpty()) {
            currentUser.setProfilePicUrl(request.getProfilePicUrl());
        }
        return userRepository.save(currentUser);
    }

}
