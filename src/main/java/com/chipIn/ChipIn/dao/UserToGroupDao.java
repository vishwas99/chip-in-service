package com.chipIn.ChipIn.dao;

import com.chipIn.ChipIn.dto.UserToGroupDto;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.entities.UserGroup;
import com.chipIn.ChipIn.entities.UserGroupsId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
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

    public Set<UUID> getGroupsByUserId(UUID userId){
        try{
            String sql = "SELECT groupid FROM user_groups WHERE userid=:userId";
            return (Set<UUID>) entityManager.createNativeQuery(sql)
                    .setParameter("userId", userId)
                    .getResultStream()
                    .map(obj -> (UUID) obj)
                    .collect(Collectors.toSet());
        }catch (Exception e){
            log.error("Error fetching GroupIds from UserId : {} ", userId);
            throw new RuntimeException("Error while fetching groups", e);
        }
    }
    public List<Group> getGroupsObjectByUserId(java.util.UUID userId){
        try{
            // select actual group rows and map them to the Group entity to avoid Object[] results
            String sql = "SELECT g.* FROM chipin.groups g " +
                    "JOIN chipin.user_groups ug ON g.groupid = ug.groupid " +
                    "WHERE ug.userid = :userId";

            return entityManager.createNativeQuery(sql, Group.class)
                    .setParameter("userId", userId)
                    .getResultList();
        }catch (Exception e){
            log.error("Error fetching Groups for UserId : {} ", userId, e);
            throw new RuntimeException("Error while fetching groups", e);
        }
    }

    public float getUserGroupMoneyOwed(UUID userId, UUID groupId){
        try{
            String sql = "SELECT moneyowed FROM chipin.user_groups WHERE userid=:userId and groupid=:groupId";
            return (float) entityManager.createNativeQuery(sql)
                    .setParameter("userId", userId)
                    .setParameter("groupId", groupId)
                    .getSingleResult();
        } catch (Exception e){
            throw e;
        }
    }

    public void setUserGroupMoneyOwed(UUID userId, UUID groupId, float amountToBeAdded){
        try{

            UserGroupsId userGroupsId = new UserGroupsId(userId, groupId);

            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<UserGroup> cq = cb.createQuery(UserGroup.class);
            Root<UserGroup> root = cq.from(UserGroup.class);
            cq.select(root).where(cb.equal(root.get("userGroupsId"), userGroupsId));

            UserGroup userGroup = entityManager.createQuery(cq).getResultStream().findFirst().orElse(null);

            if(userGroup != null){
                userGroup.setMoneyOwed(userGroup.getMoneyOwed() + amountToBeAdded);
                entityManager.persist(userGroup);
            }
        }catch (Exception e){
            throw e;
        }
    }


}
