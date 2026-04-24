package com.chipIn.ChipIn.services;


import com.chipIn.ChipIn.dto.FriendResponse;
import com.chipIn.ChipIn.dto.LoginRequest;
import com.chipIn.ChipIn.dto.SignupRequest;
import com.chipIn.ChipIn.dto.UpdateProfileRequest;
import com.chipIn.ChipIn.entities.GroupMember;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.entities.enums.AuthProvider;
import com.chipIn.ChipIn.entities.enums.UserStatus;
import com.chipIn.ChipIn.repository.GroupMemberRepository;
import com.chipIn.ChipIn.repository.UserRepository;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository; // Re-added
    private final PasswordEncoder passwordEncoder;
    private final CurrencyRepository currencyRepository;

    public User registerUser(SignupRequest signupRequest){
        if(userRepository.existsByEmail(signupRequest.getEmail())){
            throw new RuntimeException("User already exists");
        }

        // Set default currency to INR
        Currency defaultCurrency = currencyRepository.findByCode("INR").orElseThrow(
                () -> new RuntimeException("Default currency INR not found")
        );

        User user = User.builder().name(signupRequest.getName())
                .email(signupRequest.getEmail())
                .password(passwordEncoder.encode(signupRequest.getPassword()))
                .name(signupRequest.getName())
                .phone(signupRequest.getPhone())
                .authProvider(AuthProvider.LOCAL)
                .status(UserStatus.ACTIVE)
                .isRegistered(true)
                .defaultCurrencyId(defaultCurrency.getCurrencyId())
                .build();

        return userRepository.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return null;
    }

    public User getUserByEmail(String email){
        Optional<User> user = userRepository.findByEmail(email);
        return user.orElse(null);
    }

    public void disableUser(String email){
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new RuntimeException("User not found")
        );
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);
    }

    public void enableUser(String email){
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new RuntimeException("User not found")
        );
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    public User updateProfile(User currentUser, UpdateProfileRequest request) {
        if (request.getName() != null && !request.getName().isEmpty()) {
            currentUser.setName(request.getName());
        }
        if (request.getPhone() != null && !request.getPhone().isEmpty()) {
            currentUser.setPhone(request.getPhone());
        }
        if (request.getProfilePicUrl() != null && !request.getProfilePicUrl().isEmpty()) {
            currentUser.setProfilePicUrl(request.getProfilePicUrl());
        }
        return userRepository.save(currentUser);
    }

    public List<User> searchUsers(String query) {
        return userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query);
    }

    public List<FriendResponse> getFriends(UUID userId) {
        log.info("Fetching friends for userId: {}", userId);

        // 1. Find all groups the user is a member of
        List<GroupMember> memberships = groupMemberRepository.findByIdUserId(userId);
        log.info("Found {} group memberships for userId: {}", memberships.size(), userId);

        // 2. Get all unique user IDs from those groups
        Set<UUID> friendIds = new HashSet<>();
        for (GroupMember membership : memberships) {
            UUID groupId = membership.getGroup().getGroupId();
            log.debug("Processing group: {}", groupId);
            List<GroupMember> groupMembers = groupMemberRepository.findByGroupGroupId(groupId);
            groupMembers.forEach(member -> friendIds.add(member.getUser().getUserid()));
        }
        log.info("Collected {} unique user IDs from shared groups before filtering current user.", friendIds.size());

        // 3. Remove the current user's ID
        friendIds.remove(userId);
        log.info("Collected {} unique user IDs from shared groups after filtering current user.", friendIds.size());


        // 4. Fetch the User objects for the friend IDs
        List<User> friends = userRepository.findAllById(friendIds);
        log.info("Fetched {} User objects for friend IDs.", friends.size());

        // 5. Map to DTO
        List<FriendResponse> friendResponses = friends.stream()
                .map(friend -> FriendResponse.builder()
                        .userId(friend.getUserid())
                        .name(friend.getName())
                        .email(friend.getEmail())
                        .profilePicUrl(friend.getProfilePicUrl())
                        .build())
                .collect(Collectors.toList());
        log.info("Returning {} FriendResponse objects.", friendResponses.size());
        return friendResponses;
    }

}
