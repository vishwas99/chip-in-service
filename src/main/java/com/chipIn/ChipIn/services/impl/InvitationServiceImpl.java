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
import com.chipIn.ChipIn.services.InvitationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class InvitationServiceImpl implements InvitationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository; // Inject GroupRepository

    @Autowired
    private GroupMemberRepository groupMemberRepository; // Inject GroupMemberRepository

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.invitation.expiry.hours:24}")
    private int invitationExpiryHours;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    @Transactional
    public User inviteUser(InviteRequest inviteRequest) {
        Optional<User> existingUser = userRepository.findByEmail(inviteRequest.getEmail());

        User userToInvite;

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            // If user exists but is pending invite, re-send invite or update token
            if (user.getStatus() == UserStatus.PENDING_INVITE) {
                user.setInvitationToken(UUID.randomUUID().toString());
                user.setInvitationTokenExpiryDate(LocalDateTime.now().plusHours(invitationExpiryHours));
                userRepository.save(user);
                userToInvite = user;
            } else {
                throw new IllegalArgumentException("User with this email already exists and is not pending invitation.");
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

        // If a groupId is provided, try to add the invited user to the group.
        // IMPORTANT: invitation should not fail completely if the groupId is invalid — just create the user and log the issue.
        if (inviteRequest.getGroupId() != null) {
            var maybeGroup = groupRepository.findById(inviteRequest.getGroupId());
            if (maybeGroup.isEmpty()) {
                // Do not fail the whole invite flow for an invalid group id. Log and continue.
                log.warn("Invite requested with non-existent groupId {}. User {} created without group membership.", inviteRequest.getGroupId(), userToInvite.getEmail());
            } else {
                Group group = maybeGroup.get();

                // Check if the user is already a member of the group
                GroupMemberId membershipId = new GroupMemberId(group.getGroupId(), userToInvite.getUserid());
                if (groupMemberRepository.findById(membershipId).isPresent()) {
                    // If already member, just skip adding (don't fail the invite)
                    log.info("User {} is already a member of group {}. Skipping membership add.", userToInvite.getEmail(), group.getGroupId());
                } else {
                    GroupMember groupMember = GroupMember.builder()
                            .id(membershipId)
                            .user(userToInvite)
                            .group(group)
                            .isAdmin(false) // Invited users are not admins by default
                            .build();
                    groupMemberRepository.save(groupMember);
                }
            }
        }

        sendInvitationEmail(userToInvite, generateInvitationLink(userToInvite.getInvitationToken()));
        return userToInvite;
    }

    @Override
    public void sendInvitationEmail(User invitedUser, String invitationLink) {
        // Mocking email sending for now
        log.info("MOCK EMAIL SENT to: {} ({}) with invitation link: {}", invitedUser.getEmail(), invitedUser.getName(), invitationLink);
    }

    @Override
    @Transactional
    public User registerInvitedUser(String token, String password) {
        User user = userRepository.findByInvitationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invitation token."));

        if (user.getInvitationTokenExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invitation token has expired.");
        }

        if (user.getStatus() != UserStatus.PENDING_INVITE) {
            throw new IllegalArgumentException("User is not in a pending invitation state.");
        }

        user.setPassword(passwordEncoder.encode(password));
        user.setStatus(UserStatus.ACTIVE); // Assuming ACTIVE is the status for registered users
        user.setIsRegistered(true);
        user.setInvitationToken(null);
        user.setInvitationTokenExpiryDate(null);

        return userRepository.save(user);
    }

    private String generateInvitationLink(String token) {
        return frontendUrl + "/register?token=" + token;
    }
}
