package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.FriendResponse;
import com.chipIn.ChipIn.dto.UpdateProfileRequest;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.services.UserService;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/users")
@Validated
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for user profile management")
public class UserController extends BaseController {

    private final UserService userService;
    private final CurrencyRepository currencyRepository;

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

    @Operation(summary = "Get current user's default currency", description = "Fetches the default currency of the currently authenticated user")
    @GetMapping("/me/default-currency")
    public ResponseEntity<Currency> getDefaultCurrency() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Currency currency = currencyRepository.findById(currentUser.getDefaultCurrencyId()).orElseThrow(
                () -> new RuntimeException("Default currency not found")
        );
        return ResponseEntity.ok(currency);
    }

    @Operation(summary = "Get all friends of the user", description = "Friends of the currently authenticated user")
    @GetMapping("/friends")
    public ResponseEntity<List<FriendResponse>> getFriends(){
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(userService.getFriends(currentUser.getUserid()));
    }

    @Operation(summary = "Search users by name or email", description = "Searches for users by name or email (case-insensitive). Useful for populating a dropdown or autocomplete field when adding users to a group.")
    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(@RequestParam String query) {
        return ResponseEntity.ok(userService.searchUsers(query));
    }
}
