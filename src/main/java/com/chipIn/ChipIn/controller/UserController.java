package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.FriendResponse;
import com.chipIn.ChipIn.dto.UpdateProfileRequest;
import com.chipIn.ChipIn.dto.UserResponse;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.services.UserService;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;


@RestController
@RequestMapping("/api/users")
@Validated
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for user profile management")
public class UserController extends BaseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;
    private final CurrencyRepository currencyRepository;

    @Operation(summary = "Get current user profile")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(UserResponse.from(currentUser));
    }

    @Operation(summary = "Update current user profile",
            description = "Updates the name, phone, or profile picture of the authenticated user.")
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User updated = userService.updateProfile(currentUser, request);
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    /**
     * Self-service suspend. Acts on the authenticated principal only;
     * admin-driven moderation lives elsewhere. The previous endpoint took
     * an arbitrary `email` query param and let any caller suspend any user.
     */
    @Operation(summary = "Disable the authenticated user's own account")
    @PostMapping("/me/disable")
    public ResponseEntity<String> disableSelf() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.disableSelf(currentUser);
        return ResponseEntity.ok("User disabled.");
    }

    @Operation(summary = "Re-enable the authenticated user's own account")
    @PostMapping("/me/enable")
    public ResponseEntity<String> enableSelf() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.enableSelf(currentUser);
        return ResponseEntity.ok("User enabled.");
    }

    @Operation(summary = "Get current user's default currency")
    @GetMapping("/me/default-currency")
    public ResponseEntity<Currency> getDefaultCurrency() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (currentUser.getDefaultCurrencyId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Default currency not set");
        }
        Currency currency = currencyRepository.findById(currentUser.getDefaultCurrencyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Default currency not found"));
        return ResponseEntity.ok(currency);
    }

    @Operation(summary = "Get friends of the authenticated user (paginated)")
    @GetMapping("/friends")
    public ResponseEntity<Page<FriendResponse>> getFriends(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return ResponseEntity.ok(userService.getFriends(currentUser.getUserid(), pageable));
    }

    @Operation(summary = "Search users by name or email (paginated)")
    @GetMapping("/search")
    public ResponseEntity<Page<FriendResponse>> searchUsers(
            @RequestParam @NotBlank @Size(min = 2, max = 64) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return ResponseEntity.ok(userService.searchUsersForDirectory(query, pageable));
    }
}
