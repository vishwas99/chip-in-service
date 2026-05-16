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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        Optional<User> existingUser = userRepository.findByEmail(signupRequest.getEmail());

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (user.getStatus() == UserStatus.PENDING_INVITE) {
                if (!user.getName().equals(signupRequest.getName())) {
                    user.setName(signupRequest.getName());
                }
                user.setPassword(passwordEncoder.encode(signupRequest.getPassword()));
                user.setPhone(signupRequest.getPhone());
                user.setStatus(UserStatus.ACTIVE);
                user.setIsRegistered(true);
                user.setInvitationToken(null);
                user.setInvitationTokenExpiryDate(null);
                return userRepository.save(user);
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists");
            }
        }

        Currency defaultCurrency = currencyRepository.findByCode("INR")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Default currency INR not seeded"));

        User user = User.builder().name(signupRequest.getName())
                .email(signupRequest.getEmail())
                .password(passwordEncoder.encode(signupRequest.getPassword()))
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
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email){
        return userRepository.findByEmail(email).orElse(null);
    }

    /**
     * Suspend the authenticated user's own account. Admin-driven moderation
     * (suspending other users) lives in a separate admin surface that this
     * service intentionally does not expose.
     */
    public void disableSelf(User currentUser) {
        User user = userRepository.findById(currentUser.getUserid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);
    }

    public void enableSelf(User currentUser) {
        User user = userRepository.findById(currentUser.getUserid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
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

    @Transactional(readOnly = true)
    public List<User> searchUsers(String query) {
        return userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(query);
    }

    /**
     * Directory search, paginated. Never returns the password hash or any
     * token-bearing field (uses {@link FriendResponse}).
     */
    @Transactional(readOnly = true)
    public Page<FriendResponse> searchUsersForDirectory(String query, Pageable pageable) {
        List<User> all = searchUsers(query);
        return paginate(all, pageable, this::toFriendResponse);
    }

    @Transactional(readOnly = true)
    public Page<FriendResponse> getFriends(UUID userId, Pageable pageable) {
        List<GroupMember> memberships = groupMemberRepository.findByIdUserId(userId);

        Set<UUID> friendIds = new HashSet<>();
        for (GroupMember membership : memberships) {
            UUID groupId = membership.getGroup().getGroupId();
            List<GroupMember> groupMembers = groupMemberRepository.findByGroupGroupId(groupId);
            groupMembers.forEach(member -> friendIds.add(member.getUser().getUserid()));
        }
        friendIds.remove(userId);

        if (friendIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<User> friends = userRepository.findByIdInActive(friendIds);
        log.debug("getFriends userId={} count={}", userId, friends.size());
        return paginate(friends, pageable, this::toFriendResponse);
    }

    private FriendResponse toFriendResponse(User u) {
        return FriendResponse.builder()
                .userId(u.getUserid())
                .name(u.getName())
                .email(u.getEmail())
                .profilePicUrl(u.getProfilePicUrl())
                .build();
    }

    /** In-memory pagination helper for endpoints whose underlying repo method
     *  doesn't yet support {@link Pageable}. Switch to a paged JPA query when
     *  these lists outgrow the page size. */
    private static <S, T> Page<T> paginate(List<S> all, Pageable pageable, java.util.function.Function<S, T> mapper) {
        int total = all.size();
        int start = (int) Math.min((long) pageable.getOffset(), total);
        int end = (int) Math.min(start + (long) pageable.getPageSize(), total);
        List<T> slice = all.subList(start, end).stream().map(mapper).collect(Collectors.toList());
        return new PageImpl<>(slice, pageable, total);
    }
}
