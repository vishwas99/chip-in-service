package com.chipIn.ChipIn.repository;

import com.chipIn.ChipIn.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Find active user by email (not deleted)
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.isDeleted = false")
    Optional<User> findByEmail(@Param("email") String email);

    // Check if email exists (used during signup)
    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.invitationToken = :token AND u.isDeleted = false")
    Optional<User> findByInvitationToken(@Param("token") String invitationToken);

    // Search for users by name or email (case-insensitive, excluding deleted)
    @Query("SELECT u FROM User u WHERE (LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) AND u.isDeleted = false")
    List<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(@Param("query") String query);

    // Find active user by ID (not deleted)
    @Query("SELECT u FROM User u WHERE u.userid = :userId AND u.isDeleted = false")
    Optional<User> findByIdActive(@Param("userId") UUID userId);

    // Find multiple active users by IDs (not deleted)
    @Query("SELECT u FROM User u WHERE u.userid IN :userIds AND u.isDeleted = false")
    List<User> findByIdInActive(@Param("userIds") Iterable<UUID> userIds);
}
