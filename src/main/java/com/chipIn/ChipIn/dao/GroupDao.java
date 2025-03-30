package com.chipIn.ChipIn.dao;

import com.chipIn.ChipIn.entities.Group;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Repository
@Transactional
public class GroupDao {

    @PersistenceContext
    private EntityManager entityManager;

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

}
