package com.chipIn.ChipIn.repository;

import com.chipIn.ChipIn.entities.GroupMember;
import com.chipIn.ChipIn.entities.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    @Query("SELECT gm FROM GroupMember gm JOIN FETCH gm.group g JOIN FETCH g.defaultCurrency WHERE gm.id.userId = :userId AND g.isDeleted = false")
    List<GroupMember> findByIdUserId(@Param("userId") UUID userId);

    @Query("SELECT gm FROM GroupMember gm JOIN FETCH gm.user WHERE gm.id.groupId = :groupId AND gm.user.isDeleted = false")
    List<GroupMember> findByGroupGroupId(@Param("groupId") UUID groupId);

    @Query("SELECT gm FROM GroupMember gm WHERE gm.id.groupId = :groupId AND gm.id.userId = :userId")
    GroupMember findByGroupIdAndUserId(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

}
