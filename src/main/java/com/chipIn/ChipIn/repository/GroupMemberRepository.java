package com.chipIn.ChipIn.repository;

import com.chipIn.ChipIn.entities.GroupMember;
import com.chipIn.ChipIn.entities.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {
}
