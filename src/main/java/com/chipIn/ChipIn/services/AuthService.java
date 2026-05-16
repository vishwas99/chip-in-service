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
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("Invalid credentials");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String jwtToken = jwtService.generateToken(user);
        log.info("Login successful for userId={}", user.getUserid());

        return LoginResponse.builder()
                .token(jwtToken)
                .userId(user.getUserid())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }

    /**
     * Invalidates the authenticated user's current token by rotating their
     * tokenVersion. Acts on the principal — does not accept an email param
     * because that would let any caller log out arbitrary users.
     */
    public void logout(User currentUser) {
        if (currentUser == null) {
            throw new RuntimeException("Authentication required");
        }
        User user = userRepository.findById(currentUser.getUserid())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Integer version = user.getTokenVersion() == null ? 1 : user.getTokenVersion();
        user.setTokenVersion(version + 1);
        userRepository.save(user);
        log.info("Logout / token rotation for userId={}", user.getUserid());
    }
}
