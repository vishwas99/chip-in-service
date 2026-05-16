package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.LoginRequest;
import com.chipIn.ChipIn.dto.LoginResponse;
import com.chipIn.ChipIn.dto.SignupRequest;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.services.AuthService;
import com.chipIn.ChipIn.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController extends BaseController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<LoginResponse> signUp(@Valid @RequestBody SignupRequest signupRequest) {
        User saved = userService.registerUser(signupRequest);
        return ResponseEntity.ok(LoginResponse.builder()
                .userId(saved.getUserid())
                .name(saved.getName())
                .email(saved.getEmail())
                .build());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Logout acts on the authenticated user — no body, no email param. This
     * closes the previous defect where any caller could log out anyone by
     * passing their email.
     */
    @PostMapping("/logout")
    public ResponseEntity<String> logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User currentUser)) {
            return ResponseEntity.status(401).body("Authentication required");
        }
        authService.logout(currentUser);
        return ResponseEntity.ok("Logout successful, token invalidated.");
    }
}
