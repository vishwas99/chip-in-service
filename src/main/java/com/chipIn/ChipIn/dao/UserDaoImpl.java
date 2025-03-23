package com.chipIn.ChipIn.dao;

import com.chipIn.ChipIn.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@Transactional
public class UserDaoImpl {

    @PersistenceContext
    private EntityManager entityManager;

    public List<User> getUsers(){
        return entityManager.createQuery("SELECT u FROM User u", User.class).getResultList();
    }

    public UUID addUser(User userData){
        entityManager.persist(userData);
        entityManager.flush();
        // Verify the user was created by fetching it
        User createdUser = entityManager.find(User.class, userData.getUserId());
        if (createdUser == null) {
            throw new TransactionException("Failed to create user"){
                @Override
                public String getMessage() {
                    return super.getMessage();
                }
            };
        }

        return createdUser.getUserId();
//        return entityManager.createQuery("Select u.userId from User u", User.class).getSingleResult().getUserId();
    }

}
