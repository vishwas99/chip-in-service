package com.chipIn.ChipIn.dao;

import com.chipIn.ChipIn.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public class UserDao {

    @PersistenceContext
    private EntityManager entityManager;

    public List<User> getUsers(){
        TypedQuery<User> query = entityManager.createQuery("SELECT u FROM User u", User.class);
        return query.getResultList();
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
    }

    public User getUserById(UUID userId){
        return entityManager.find(User.class, userId);
    }

    public Optional<User> getUserByEmail(String email) {
        try {
            User user = entityManager.createQuery(
                            "SELECT u FROM User u WHERE u.email = :email", User.class)
                    .setParameter("email", email)
                    .getSingleResult();
            return Optional.ofNullable(user);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }


}
