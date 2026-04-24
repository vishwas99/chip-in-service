package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.LoginRequest;
import com.chipIn.ChipIn.dto.LoginResponse;
import com.chipIn.ChipIn.dto.SignupRequest;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.services.AuthService;
import com.chipIn.ChipIn.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<User> signUp(@RequestBody SignupRequest signupRequest) {
        return ResponseEntity.ok(userService.registerUser(signupRequest));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestBody LoginRequest request) {
        authService.logout(request.getEmail());
        return ResponseEntity.ok("Logout Successful, Token invalidated!");
    }
}
