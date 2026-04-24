package com.chipIn.ChipIn.entities;

import com.chipIn.ChipIn.entities.enums.AuthProvider;
import com.chipIn.ChipIn.entities.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
// 🚨 THESE ARE THE CRITICAL IMPORTS FOR YOUR ERROR 🚨
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;


import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

@Entity
@Table(name = "users") // Matches our DB Schema
@Data // Lombok: Getters, Setters, ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID userid;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String phone;

    @Column(name = "profile_pic_url")
    private String profilePicUrl;

    @Column(name = "password_hash")
    private String password; // Implements UserDetails

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider")
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "oauth_provider_id")
    private String oauthProviderId;

    @Column(name = "token_version")
    @Builder.Default
    private Integer tokenVersion = 1;

    @Column(name = "is_registered")
    @Builder.Default
    private boolean isRegistered = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING_INVITE;

    @Column(name = "is_deleted")
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "default_currency_id")
    private UUID defaultCurrencyId;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    @Column(name = "invitation_token")
    private String invitationToken;

    @Column(name = "invitation_token_expiry_date")
    private LocalDateTime invitationTokenExpiryDate;

    // Explicit setter for isRegistered to avoid Lombok boolean naming issues
    public void setIsRegistered(boolean registered) {
        isRegistered = registered;
    }

    // --- UserDetails Implementation (For Spring Security) ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList(); // We don't use Roles yet
    }

    @Override
    public String getUsername() {
        return email; // We use email as username
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return status != UserStatus.SUSPENDED; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return !isDeleted; }

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
