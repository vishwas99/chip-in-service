package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.GroupMember;
import com.chipIn.ChipIn.entities.GroupMemberId;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.repository.GroupMemberRepository;
import com.chipIn.ChipIn.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Centralised authorization helper. Every handler / service that touches
 * data scoped to a group must call one of these methods before reading
 * or mutating. Returns the resolved Group / GroupMember so callers don't
 * need a second fetch.
 *
 * Errors map to:
 *   - 404 if the group does not exist (avoids leaking existence)
 *   - 403 if the caller is not a member / not an admin
 */
@Service
@RequiredArgsConstructor
public class AccessGuard {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    public Group requireGroup(UUID groupId) {
        if (groupId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "groupId is required");
        }
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
        if (group.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
        }
        return group;
    }

    public GroupMember requireGroupMember(UUID groupId, User user) {
        Group group = requireGroup(groupId);
        return requireMembership(group, user);
    }

    public GroupMember requireGroupMember(Group group, User user) {
        return requireMembership(group, user);
    }

    public GroupMember requireGroupAdmin(UUID groupId, User user) {
        GroupMember member = requireGroupMember(groupId, user);
        if (!member.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Group admin role required");
        }
        return member;
    }

    public GroupMember requireGroupAdmin(Group group, User user) {
        GroupMember member = requireMembership(group, user);
        if (!member.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Group admin role required");
        }
        return member;
    }

    public boolean isMember(UUID groupId, UUID userId) {
        if (groupId == null || userId == null) return false;
        return groupMemberRepository.findById(new GroupMemberId(groupId, userId)).isPresent();
    }

    private GroupMember requireMembership(Group group, User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        GroupMemberId id = new GroupMemberId(group.getGroupId(), user.getUserid());
        GroupMember member = groupMemberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group"));
        if (!"ACTIVE".equalsIgnoreCase(member.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your membership is not active");
        }
        return member;
    }
}
