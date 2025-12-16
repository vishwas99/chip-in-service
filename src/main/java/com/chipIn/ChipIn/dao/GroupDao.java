package com.chipIn.ChipIn.dao;

import com.chipIn.ChipIn.dto.ExpenseResponseDto;
import com.chipIn.ChipIn.dto.UserGroupResponse;
import com.chipIn.ChipIn.entities.Group;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Repository
@Transactional
public class GroupDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    UserToGroupDao userToGroupDao;

    public UUID createGroup(Group group){
        entityManager.persist(group);
        entityManager.flush();

        try {
            return entityManager.find(Group.class, group.getGroupId()).getGroupId();
        }catch (Exception e){
            log.error(e.getMessage());
            throw e;
        }
    }

    public Group getGroupData(UUID groupId){
        return entityManager.find(Group.class, groupId);
    }

    public Set<Group> getGroupSet(Set<UUID> groupIds) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Group> cq = cb.createQuery(Group.class);
        Root<Group> root = cq.from(Group.class);

        cq.select(root).where(root.get("id").in(groupIds));

        return new HashSet<>(entityManager.createQuery(cq).getResultList());
    }

    public List<UserGroupResponse> getGroupsByUserId(UUID userId){
//       Get all groupId given userId
        Set<UUID> groupIds = userToGroupDao.getGroupsByUserId(userId);

        log.info("All Groups for given  user : " + userId + " " + groupIds.toString());

        List<UserGroupResponse> userGroupResponses = new ArrayList<>();

//        Get all groups given List of groupIds
        Set<Group> groups = getGroupSet(groupIds);
//        Get UserGroup MoneyOwed for each group of the user
        for(Group group: groups){
            UserGroupResponse u = new UserGroupResponse();
            u.setGroup(group);
            ExpenseResponseDto expenseResponseDto = new ExpenseResponseDto();
//            groupExpenseDto.setCurrency();
//            u.setMoneyOwed(userToGroupDao.getUserGroupMoneyOwed(userId, group.getGroupId()));
            userGroupResponses.add(u);
        }
        return userGroupResponses;
    }

    public List<Group> getAllGroupsByUserId(UUID userId){
        Set<UUID> groupIds = userToGroupDao.getGroupsByUserId(userId);

        List<UserGroupResponse> userGroupResponses = new ArrayList<>();

        return getGroupSet(groupIds).stream().toList();
    }
}
