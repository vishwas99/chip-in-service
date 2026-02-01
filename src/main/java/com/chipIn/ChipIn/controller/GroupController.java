package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.AddMemberRequest;
import com.chipIn.ChipIn.dto.CreateGroupRequest;
import com.chipIn.ChipIn.dto.GroupDashboardResponse;
import com.chipIn.ChipIn.dto.GroupResponse;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import com.chipIn.ChipIn.services.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController extends  BaseController{

    private final GroupService groupService;
    private final CurrencyRepository currencyRepository;

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
                .defaultCurrency(currencyRepository.findById(request.getDefaultCurrencyId()).orElse(null))
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

}
