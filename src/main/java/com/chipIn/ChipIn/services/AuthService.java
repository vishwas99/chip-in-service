package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dto.LoginRequest;
import com.chipIn.ChipIn.dto.LoginResponse;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.entities.enums.UserStatus;
import com.chipIn.ChipIn.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("User is not active");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        // Update Last Login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // GENERATE TOKEN
        String jwtToken = jwtService.generateToken(user);

        return LoginResponse.builder()
                .token(jwtToken)
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }

    public void logout(String email) {
        // 1. Find the user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Increment the token version
        user.setTokenVersion(user.getTokenVersion() + 1);

        // 3. Save the update
        userRepository.save(user);
    }
}
