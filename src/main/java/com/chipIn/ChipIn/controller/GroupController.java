package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.*;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.GroupCurrency;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import com.chipIn.ChipIn.services.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

    @Operation(summary = "Create a new group",
            description = "Creates a new group and assigns the authenticated user as an admin member.")
    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Group groupData = Group.builder()
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .type(request.getType())
                .simplifyDebt(request.isSimplifyDebt())
                .defaultCurrency(currencyRepository.findById(request.getDefaultCurrencyId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "defaultCurrencyId is invalid")))
                .build();

        Group savedGroup = groupService.createGroup(groupData, currentUser);

        GroupResponse response = GroupResponse.builder()
                .groupId(savedGroup.getGroupId())
                .name(savedGroup.getName())
                .description(savedGroup.getDescription())
                .imageUrl(savedGroup.getImageUrl())
                .type(savedGroup.getType())
                .simplifyDebt(savedGroup.isSimplifyDebt())
                .defaultCurrency(savedGroup.getDefaultCurrency())
                .createdAt(savedGroup.getCreatedAt())
                .isAdmin(true)
                .build();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Add an existing user to a group", description = "Requires admin privileges within the group.")
    @PostMapping("/{groupId}/members")
    public ResponseEntity<String> addMember(
            @PathVariable UUID groupId,
            @Valid @RequestBody AddMemberRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        groupService.addMemberToGroup(groupId, request, currentUser);
        return ResponseEntity.ok("Member added successfully");
    }

    @GetMapping("/{groupId}/dashboard")
    public ResponseEntity<GroupDashboardResponse> getGroupDashboard(@PathVariable UUID groupId) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(groupService.getGroupDashboard(groupId, currentUser));
    }

    @GetMapping("/{groupId}/balances")
    public ResponseEntity<GroupBalancesResponse> getGroupBalances(@PathVariable UUID groupId) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(groupService.getGroupBalances(groupId, currentUser));
    }

    @GetMapping("/me")
    public ResponseEntity<List<GroupResponse>> getMyGroups() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(groupService.getUserGroups(currentUser.getUserid()));
    }

    // -------------------- Group currency CRUD --------------------

    @Operation(summary = "List currencies (buckets and FX rate rows) for a group")
    @GetMapping("/{groupId}/currencies")
    public ResponseEntity<List<GroupCurrencyResponse>> listGroupCurrencies(@PathVariable UUID groupId) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<GroupCurrencyResponse> response = groupService.listGroupCurrencies(groupId, currentUser).stream()
                .map(GroupCurrencyResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Create a custom currency bucket for a group",
            description = "Admin-only. Creates a named bucket (e.g. 'YEN-Day1') with a fixed bucket->master rate.")
    @PostMapping("/{groupId}/currencies")
    public ResponseEntity<GroupCurrencyResponse> createGroupCurrency(
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateGroupCurrencyRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        GroupCurrency created = groupService.createGroupCurrency(groupId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(GroupCurrencyResponse.from(created));
    }

    @Operation(summary = "Update a group's custom currency bucket name / rate")
    @PutMapping("/{groupId}/currencies/{groupCurrencyId}")
    public ResponseEntity<GroupCurrencyResponse> updateGroupCurrency(
            @PathVariable UUID groupId,
            @PathVariable UUID groupCurrencyId,
            @Valid @RequestBody UpdateGroupCurrencyRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        GroupCurrency updated = groupService.updateGroupCurrency(groupId, groupCurrencyId, request, currentUser);
        return ResponseEntity.ok(GroupCurrencyResponse.from(updated));
    }

    @Operation(summary = "Soft-delete a group's custom currency bucket",
            description = "Cannot delete the base bucket or any bucket referenced by an expense.")
    @DeleteMapping("/{groupId}/currencies/{groupCurrencyId}")
    public ResponseEntity<Void> deleteGroupCurrency(
            @PathVariable UUID groupId,
            @PathVariable UUID groupCurrencyId) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        groupService.deleteGroupCurrency(groupId, groupCurrencyId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Upsert an FX rate (true->true) for the group's resolver chain")
    @PutMapping("/{groupId}/fx-rates")
    public ResponseEntity<GroupCurrencyResponse> upsertFxRate(
            @PathVariable UUID groupId,
            @Valid @RequestBody UpsertGroupFxRateRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        GroupCurrency row = groupService.upsertFxRate(groupId, request, currentUser);
        return ResponseEntity.ok(GroupCurrencyResponse.from(row));
    }

    @GetMapping("/users/{groupId}")
    public ResponseEntity<List<FriendResponse>> getGroupUsers(@PathVariable UUID groupId) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(groupService.getGroupUsers(groupId, currentUser));
    }

    /**
     * Soft-deletes the group. Both modes mark the group (and any contained
     * expenses) as {@code is_deleted=true}; no row is ever removed.
     *
     * <ul>
     *   <li>{@code force=false} (default): refuses to delete unless every
     *       member's balance is zero — i.e. the group is fully settled.</li>
     *   <li>{@code force=true}: deletes even with unsettled balances. Use
     *       sparingly; this is the moral equivalent of "abandon".</li>
     * </ul>
     *
     * No data is permanently destroyed.
     */
    @Operation(summary = "Soft-delete a group", description = "Requires admin privileges within the group.")
    @DeleteMapping("/{groupId}")
    public ResponseEntity<String> deleteGroup(
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "false") boolean force) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        groupService.deleteGroup(groupId, force, currentUser);
        return ResponseEntity.ok("Group deleted successfully");
    }
}
