package com.chipIn.ChipIn.repository;

import com.chipIn.ChipIn.entities.GroupMember;
import com.chipIn.ChipIn.entities.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    List<GroupMember> findByIdUserId(UUID userId);


}
