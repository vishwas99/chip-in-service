package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dao.UserDao;
import com.chipIn.ChipIn.entities.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
//@RequestMapping()
public class LoginController {

    private final AuthenticationManager authenticationManager;

    @Autowired
    private UserDao userDao;

    private final PasswordEncoder passwordEncoder;
//    private final DaoAuthenticationProvider daoAuthenticationProvider;

    public LoginController(AuthenticationManager authenticationManager, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
    }

//    @PostMapping("/login")
//    public ResponseEntity<Void> login(@RequestBody LoginRequest loginRequest) {
//        Authentication authenticationRequest =
//                UsernamePasswordAuthenticationToken.unauthenticated(loginRequest.username(), loginRequest.password());
//        Authentication authenticationResponse =
//                this.authenticationManager.authenticate(authenticationRequest);
//        // ...
//        return ResponseEntity.ok().build();
//    }

    @PostMapping("/login")
    public ResponseEntity<?> login(HttpServletRequest request, @RequestBody Map<String, String> credentials) {
        System.out.println(credentials);
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(credentials.get("email"), credentials.get("password"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth); // Store authentication in context

        HttpSession session = request.getSession(); // Create or retrieve session
        session.setAttribute("USER", auth.getPrincipal()); // Store user details in session

        return ResponseEntity.ok(Map.of("message", "Login successful", "sessionId", session.getId()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        System.out.println(user);

        if ( userDao.getUserByEmail(user.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userDao.addUser(user);
        return ResponseEntity.ok("User registered successfully");
    }

    public record LoginRequest(String username, String password) {
    }

}
