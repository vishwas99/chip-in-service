package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.InviteRequest;
import com.chipIn.ChipIn.dto.RegisterInvitedUserRequest;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.services.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitations", description = "API for managing user invitations and registration")
public class InvitationController {

    private final InvitationService invitationService;

    @Operation(summary = "Invite a user to the platform",
            description = "If groupId is provided, the caller must be an admin of that group.")
    @PostMapping("/invite")
    public ResponseEntity<String> inviteUser(@Valid @RequestBody InviteRequest inviteRequest) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User invitedUser = invitationService.inviteUser(inviteRequest, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body("Invitation sent to " + invitedUser.getEmail());
    }

    @Operation(summary = "Register an invited user (no auth required — token-bearing)")
    @PostMapping("/register")
    public ResponseEntity<String> registerInvitedUser(@Valid @RequestBody RegisterInvitedUserRequest request) {
        User registeredUser = invitationService.registerInvitedUser(request.getToken(), request.getPassword());
        return ResponseEntity.ok("User " + registeredUser.getEmail() + " registered successfully.");
    }
}
