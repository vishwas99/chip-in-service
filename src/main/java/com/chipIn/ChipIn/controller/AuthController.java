package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.LoginRequest;
import com.chipIn.ChipIn.dto.LoginResponse;
import com.chipIn.ChipIn.dto.SignupRequest;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.services.AuthService;
import com.chipIn.ChipIn.services.UserService;
import com.chipIn.ChipIn.util.ResponseWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController extends BaseController{

    private final AuthService authService;
    private final UserService userService;


    @PostMapping("/signup")
    public ResponseEntity<ResponseWrapper<User>> signUp(@RequestBody SignupRequest signupRequest){
        return ResponseEntity.ok(ResponseWrapper.success(userService.registerUser(signupRequest)));
    }

    @PostMapping("/login")
    public ResponseEntity<ResponseWrapper<LoginResponse>> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(ResponseWrapper.success(authService.login(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ResponseWrapper<String>> logout(@RequestBody LoginRequest request) {
        authService.logout(request.getEmail());
        return ResponseEntity.ok(ResponseWrapper.success("Logout Successful, Token invalidated!"));
    }

}
