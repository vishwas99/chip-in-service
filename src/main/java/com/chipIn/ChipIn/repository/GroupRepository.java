package com.chipIn.ChipIn.repository;

import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {



}
