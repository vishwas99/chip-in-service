package com.chipIn.ChipIn.dao;

import com.chipIn.ChipIn.dto.UserToGroupDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Repository
@Transactional
public class UserToGroupDao {

    @PersistenceContext
    private EntityManager entityManager;

    private static final String GET_USERS_BY_GROUP_QUERY =
            "SELECT userid FROM user_groups WHERE groupid = :groupId";


    @Transactional
    public boolean addMemberToGroup(UserToGroupDto userToGroupDto){
        log.info("Adding member to group");
        try{
            entityManager.createNativeQuery("INSERT INTO user_groups (groupid, userid) VALUES (:groupId, :userId)")
                    .setParameter("groupId", userToGroupDto.getGroupId())
                    .setParameter("userId", userToGroupDto.getUserId())
                    .executeUpdate();
            return true;
        }catch (Exception e){
            log.error(e.getMessage());
            throw e;
        }
    }

    public Set<UUID> getUserByGroupId(UUID groupId) {
        try {
            String sql = "SELECT userid FROM chipin.user_groups WHERE groupid = :groupId"; // ✅ Native Query

            return (Set<UUID>) entityManager.createNativeQuery(sql)
                    .setParameter("groupId", groupId)
                    .getResultStream()
                    .map(obj -> (UUID) obj)
                    .collect(Collectors.toSet());

        } catch (Exception e) {
            log.error("Error fetching users for groupId: {}", groupId, e);
            throw new RuntimeException("Error while fetching members", e);
        }
    }


}
