package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Optional;

public class UserDAOImpl implements UserDAO {
    private final EntityManager em;

    public UserDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<User> query(TypedQuery<User> query) {
        return query.getResultList();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        var query = em.createQuery("from User where username = :username", User.class);
        query.setParameter("username", username);
        return query.getResultList().stream().findFirst();
    }

    @Override
    public <S extends User> S save(S entity) {
        // If the user already exists and the password hash is not set, keep the existing password hash
        if (entity.getId() != null && entity.getId() > 0L) {
            var existing = findById(entity.getId());
            if (existing.isPresent() && entity.getPassword_hash() == null) {
                entity.setPassword_hash(existing.get().getPassword_hash());
            }
            em.merge(entity);
        }
        else {
            em.persist(entity);
        }
        return entity;
    }

    @Override
    public <S extends User> Iterable<S> saveAll(Iterable<S> entities) {
        for (S entity : entities) {
            em.persist(entity);
        }
        return entities;
    }

    @Override
    public Optional<User> findById(Long aLong) {
        return Optional.ofNullable(em.find(User.class, aLong));
    }

    @Override
    public boolean existsById(Long aLong) {
        return findById(aLong) != null;
    }

    @Override
    public Iterable<User> findAll() {
        return em.createQuery("from User", User.class).getResultList();
    }

    @Override
    public Iterable<User> findAllById(Iterable<Long> longs) {
        return em.createQuery("from User where id in :ids", User.class).setParameter("ids", longs).getResultList();
    }

    @Override
    public long count() {
        return em.createQuery("select count(*) from User", Integer.class).getSingleResult();
    }

    @Override
    public void deleteById(Long aLong) {
        em.remove(em.find(User.class, aLong));
    }

    @Override
    public void delete(User entity) {
        em.remove(em.merge(entity));
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> longs) {
        for (Long id : longs) {
            em.remove(em.find(User.class, id));
        }
    }

    @Override
    public void deleteAll(Iterable<? extends User> entities) {
        for (User entity : entities) {
            em.remove(em.merge(entity));
        }
    }

    @Override
    public void deleteAll() {
        em.createQuery("delete from User").executeUpdate();
    }
}
