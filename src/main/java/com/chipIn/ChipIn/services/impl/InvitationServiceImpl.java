package com.chipIn.ChipIn.services.impl;

import com.chipIn.ChipIn.dto.InviteRequest;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.GroupMember;
import com.chipIn.ChipIn.entities.GroupMemberId;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.entities.enums.UserStatus;
import com.chipIn.ChipIn.repository.GroupMemberRepository;
import com.chipIn.ChipIn.repository.GroupRepository;
import com.chipIn.ChipIn.repository.UserRepository;
import com.chipIn.ChipIn.services.AccessGuard;
import com.chipIn.ChipIn.services.EmailService;
import com.chipIn.ChipIn.services.InvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class InvitationServiceImpl implements InvitationService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessGuard accessGuard;
    private final EmailService emailService;

    @Value("${app.invitation.expiry.hours:24}")
    private int invitationExpiryHours;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    @Transactional
    public User inviteUser(InviteRequest inviteRequest, User currentUser) {
        if (inviteRequest.getGroupId() != null) {
            accessGuard.requireGroupAdmin(inviteRequest.getGroupId(), currentUser);
        }

        Optional<User> existingUser = userRepository.findByEmail(inviteRequest.getEmail());
        User userToInvite;

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (user.getStatus() == UserStatus.PENDING_INVITE) {
                user.setInvitationToken(UUID.randomUUID().toString());
                user.setInvitationTokenExpiryDate(LocalDateTime.now().plusHours(invitationExpiryHours));
                userRepository.save(user);
                userToInvite = user;
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "User with this email already exists and is not pending invitation.");
            }
        } else {
            User newUser = User.builder()
                    .name(inviteRequest.getName())
                    .email(inviteRequest.getEmail())
                    .status(UserStatus.PENDING_INVITE)
                    .isRegistered(false)
                    .invitationToken(UUID.randomUUID().toString())
                    .invitationTokenExpiryDate(LocalDateTime.now().plusHours(invitationExpiryHours))
                    .build();
            userToInvite = userRepository.save(newUser);
        }

        if (inviteRequest.getGroupId() != null) {
            Group group = groupRepository.findById(inviteRequest.getGroupId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group not found"));

            GroupMemberId membershipId = new GroupMemberId(group.getGroupId(), userToInvite.getUserid());
            if (groupMemberRepository.findById(membershipId).isEmpty()) {
                GroupMember groupMember = GroupMember.builder()
                        .id(membershipId)
                        .user(userToInvite)
                        .group(group)
                        .isAdmin(false)
                        .build();
                groupMemberRepository.save(groupMember);
            }
        }

        sendInvitationEmail(userToInvite, generateInvitationLink(userToInvite.getInvitationToken()));
        return userToInvite;
    }

    @Override
    public void sendInvitationEmail(User invitedUser, String invitationLink) {
        // The invitation token is single-use and high-entropy — log only the
        // recipient, never the link.
        log.info("Invitation email queued userId={} email={}",
                invitedUser.getUserid(), invitedUser.getEmail());
        emailService.sendInvitationEmail(invitedUser.getEmail(), invitedUser.getName(), invitationLink);
    }

    @Override
    @Transactional
    public User registerInvitedUser(String token, String password) {
        User user = userRepository.findByInvitationToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired invitation token."));

        if (user.getInvitationTokenExpiryDate().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation token has expired.");
        }
        if (user.getStatus() != UserStatus.PENDING_INVITE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not in a pending invitation state.");
        }

        user.setPassword(passwordEncoder.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        user.setIsRegistered(true);
        user.setInvitationToken(null);
        user.setInvitationTokenExpiryDate(null);
        return userRepository.save(user);
    }

    private String generateInvitationLink(String token) {
        return frontendUrl + "/register?token=" + token;
    }
}
