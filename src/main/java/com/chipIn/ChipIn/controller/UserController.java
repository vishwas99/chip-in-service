package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.UpdateProfileRequest;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/users")
@Validated
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for user profile management")
public class UserController extends BaseController {

    private final UserService userService;

    @Operation(summary = "Get current user profile", description = "Fetches the profile of the currently authenticated user")
    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(currentUser);
    }

    @Operation(summary = "Update current user profile", description = "Updates the name, phone, or profile picture of the authenticated user")
    @PutMapping("/me")
    public ResponseEntity<User> updateProfile(@RequestBody UpdateProfileRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User updatedUser = userService.updateProfile(currentUser, request);
        return ResponseEntity.ok(updatedUser);
    }

    @Operation(summary = "Disable user", description = "Disables a user account (Admin only usually, currently open based on email)")
    @PostMapping("/disable")
    public ResponseEntity<String> disableUser(@RequestParam String email) {
        userService.disableUser(email);
        return ResponseEntity.ok("User disabled successfully.");
    }

    @Operation(summary = "Enable user", description = "Enables a disabled user account")
    @PostMapping("/enable")
    public ResponseEntity<String> enableUser(@RequestParam String email) {
        userService.enableUser(email);
        return ResponseEntity.ok("User enabled successfully.");
    }
}
