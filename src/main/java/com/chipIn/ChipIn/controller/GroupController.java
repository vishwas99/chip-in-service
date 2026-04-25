package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.*;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.GroupCurrency;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import com.chipIn.ChipIn.services.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Groups", description = "API for managing user groups and memberships")
public class GroupController extends BaseController {

    private final GroupService groupService;
    private final CurrencyRepository currencyRepository;

    /**
     * Creates a new group. The authenticated user who creates the group is automatically added as an admin member.
     *
     * @param request The request containing details for the new group.
     * @return A ResponseEntity with the created GroupResponse.
     */
    @Operation(summary = "Create a new group",
            description = "Creates a new group and assigns the authenticated user as an admin member.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Group created successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = GroupResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request, e.g., invalid currency ID",
                            content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized, if no valid token is provided")
            })
    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@RequestBody CreateGroupRequest request) {

        // 1. Get the currently logged-in user from the JWT Token
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 2. Map DTO to Entity
        Group groupData = Group.builder()
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .type(request.getType())
                .simplifyDebt(request.isSimplifyDebt())
                .defaultCurrency(currencyRepository.findById(request.getDefaultCurrencyId()).orElse(groupService.getGeoBasedCurrency()))
                .build();

        // 3. Call the Service to create group & membership
        Group savedGroup = groupService.createGroup(groupData, currentUser);

        // 4. Map Entity back to Response DTO
        GroupResponse response = GroupResponse.builder()
                .groupId(savedGroup.getGroupId())
                .name(savedGroup.getName())
                .description(savedGroup.getDescription())
                .imageUrl(savedGroup.getImageUrl())
                .type(savedGroup.getType())
                .simplifyDebt(savedGroup.isSimplifyDebt())
                .defaultCurrency(savedGroup.getDefaultCurrency())
                .createdAt(savedGroup.getCreatedAt())
                .isAdmin(true) // Creator is always admin
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Adds an existing user to a specified group. Only group admins can perform this action.
     *
     * @param groupId The ID of the group to which the member will be added.
     * @param request The request containing the email of the user to add and their admin status.
     * @return A ResponseEntity indicating the success or failure of adding the member.
     */
    @Operation(summary = "Add an existing user to a group",
            description = "Adds an already registered user to a specified group. Requires admin privileges within the group.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Member added successfully",
                            content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request, e.g., user already in group or user not found",
                            content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized, if no valid token is provided"),
                    @ApiResponse(responseCode = "403", description = "Forbidden, if the current user is not an admin of the group"),
                    @ApiResponse(responseCode = "404", description = "Group not found")
            })
    @PostMapping("/{groupId}/members")
    public ResponseEntity<String> addMember(
            @PathVariable UUID groupId,
            @RequestBody AddMemberRequest request) {

        // Get the current logged-in user
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // Add member
        groupService.addMemberToGroup(groupId, request, currentUser);

        return ResponseEntity.ok("Member added successfully");
    }

    @GetMapping("/{groupId}/dashboard")
    public ResponseEntity<GroupDashboardResponse> getGroupDashboard(@PathVariable UUID groupId) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(groupService.getGroupDashboard(groupId, currentUser.getUserid()));
    }

    @GetMapping("/{groupId}/balances")
    public ResponseEntity<GroupBalancesResponse> getGroupBalances(@PathVariable UUID groupId) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(groupService.getGroupBalances(groupId, currentUser.getUserid()));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<GroupsTabResponse> getGroupsDataByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(groupService.getGroupsDataByUserId(userId));
    }
    
    @GetMapping("/me")
    public ResponseEntity<List<GroupResponse>> getMyGroups() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("Fetching groups for user: {}", currentUser.getEmail());
        List<GroupResponse> groups = groupService.getUserGroups(currentUser.getUserid());
        log.info("Found {} groups for user.", groups.size());
        return ResponseEntity.ok(groups);
    }
    
    @PostMapping("/{groupId}/currencies/{currencyId}")
    public ResponseEntity<GroupCurrency> addGroupCurrency(
            @PathVariable UUID groupId,
            @PathVariable UUID currencyId,
            @RequestParam String name,
            @RequestParam BigDecimal exchangeRate) {
            
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(groupService.addGroupCurrency(groupId, currencyId, name, exchangeRate, currentUser));
    }

    @GetMapping("/users/{groupId}")
    public ResponseEntity<List<FriendResponse>> getGroupUsers(@PathVariable UUID groupId){
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
//        If user not part of group return 403
        if(!groupService.isUserInGroup(groupId, currentUser.getUserid())){
            return ResponseEntity.status(403).build();
        } else {
            return ResponseEntity.ok(groupService.getGroupUsers(groupId));
        }
    }

    /**
     * Deletes a group. Only admins of the group can perform this action.
     * If hardDelete is true, the group is permanently deleted. Otherwise, it is archived.
     *
     * @param groupId The ID of the group to delete.
     * @param hardDelete Optional. If true, the group is permanently deleted. Default is false.
     * @return A ResponseEntity indicating the success or failure of the delete operation.
     */
    @Operation(summary = "Delete a group",
            description = "Deletes a group by ID. Requires admin privileges within the group.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Group deleted successfully",
                            content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized, if no valid token is provided"),
                    @ApiResponse(responseCode = "403", description = "Forbidden, if the current user is not an admin of the group"),
                    @ApiResponse(responseCode = "404", description = "Group not found")
            })
    @DeleteMapping("/{groupId}")
    public ResponseEntity<String> deleteGroup(
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "false") boolean hardDelete) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        groupService.deleteGroup(groupId, hardDelete, currentUser);
        return ResponseEntity.ok("Group deleted successfully");
    }
}
