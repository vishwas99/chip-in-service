package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.InviteRequest;
import com.chipIn.ChipIn.dto.RegisterInvitedUserRequest;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.services.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invitations")
@Tag(name = "Invitations", description = "API for managing user invitations and registration")
public class InvitationController {

    @Autowired
    private InvitationService invitationService;

    /**
     * Invites a new user to the platform, optionally adding them to a specific group.
     * A temporary user account is created with PENDING_INVITE status, and an invitation email is sent.
     * If the user already exists and is in PENDING_INVITE status, the invitation is re-sent.
     *
     * @param inviteRequest The request containing the invitee's email, name, and optional groupId.
     * @return A ResponseEntity indicating the success or failure of the invitation.
     */
    @Operation(summary = "Invite a user to the platform",
            description = "Creates a temporary user account and sends an invitation email. Optionally adds the user to a group.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Invitation sent successfully",
                            content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request, e.g., user already exists or group not found",
                            content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error, e.g., email sending failed",
                            content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class)))
            })
    @PostMapping("/invite")
    public ResponseEntity<?> inviteUser(@Valid @RequestBody InviteRequest inviteRequest) {
        try {
            User invitedUser = invitationService.inviteUser(inviteRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body("Invitation sent to " + invitedUser.getEmail());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (RuntimeException e) {
            // Catching the runtime exception from email sending failure
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send invitation: " + e.getMessage());
        }
    }

    /**
     * Allows an invited user to complete their registration by setting a password.
     * The user's status is changed from PENDING_INVITE to ACTIVE.
     *
     * @param request The request containing the invitation token and the new password.
     * @return A ResponseEntity indicating the success or failure of the registration.
     */
    @Operation(summary = "Register an invited user",
            description = "Completes the registration process for a user who received an invitation, setting their password and activating their account.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "User registered successfully",
                            content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request, e.g., invalid or expired token",
                            content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class)))
            })
    @PostMapping("/register")
    public ResponseEntity<?> registerInvitedUser(@Valid @RequestBody RegisterInvitedUserRequest request) {
        try {
            User registeredUser = invitationService.registerInvitedUser(request.getToken(), request.getPassword());
            return ResponseEntity.ok("User " + registeredUser.getEmail() + " registered successfully.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
