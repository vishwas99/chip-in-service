package com.chipIn.ChipIn.repository;

import com.chipIn.ChipIn.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Find active user by email
    Optional<User> findByEmail(String email);

    // Check if email exists (used during signup)
    boolean existsByEmail(String email);

}
