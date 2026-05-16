package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.entities.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Public projection of {@link User}. Never carries the password hash,
 * token version, or invitation token. Use this anywhere a controller would
 * otherwise be tempted to return the JPA entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    UUID userId;
    String name;
    String email;
    String phone;
    String profilePicUrl;
    UserStatus status;
    UUID defaultCurrencyId;
    LocalDateTime createdAt;
    LocalDateTime lastLoginAt;

    public static UserResponse from(User user) {
        if (user == null) return null;
        return UserResponse.builder()
                .userId(user.getUserid())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .profilePicUrl(user.getProfilePicUrl())
                .status(user.getStatus())
                .defaultCurrencyId(user.getDefaultCurrencyId())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
