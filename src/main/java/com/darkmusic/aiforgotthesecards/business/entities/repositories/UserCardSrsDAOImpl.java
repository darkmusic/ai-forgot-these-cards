package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.UserCardSrs;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class UserCardSrsDAOImpl implements UserCardSrsDAO {
    private final EntityManager em;

    public UserCardSrsDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<UserCardSrs> query(TypedQuery<UserCardSrs> query) {
        return query.getResultList();
    }

    @Override
    public Optional<UserCardSrs> findByUserAndCard(User user, Card card) {
        List<UserCardSrs> results = em.createQuery(
                "from UserCardSrs where user.id = :userId and card.id = :cardId", UserCardSrs.class)
                .setParameter("userId", user.getId())
                .setParameter("cardId", card.getId())
                .getResultList();

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<UserCardSrs> findByUser(User user) {
        return em.createQuery("from UserCardSrs where user.id = :userId", UserCardSrs.class)
                .setParameter("userId", user.getId())
                .getResultList();
    }

    @Override
    public <S extends UserCardSrs> S save(S entity) {
        if (entity.getId() != null && entity.getId() > 0L) {
            em.merge(entity);
        }
        else {
            em.persist(entity);
        }

        return entity;
    }

    @Override
    public <S extends UserCardSrs> Iterable<S> saveAll(Iterable<S> entities) {
        for (S entity : entities) {
            save(entity);
        }
        return entities;
    }

    @Override
    public Optional<UserCardSrs> findById(Long id) {
        UserCardSrs entity = em.find(UserCardSrs.class, id);
        return Optional.ofNullable(entity);
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id).isPresent();
    }

    @Override
    public Iterable<UserCardSrs> findAll() {
        return em.createQuery("from UserCardSrs", UserCardSrs.class).getResultList();
    }

    @Override
    public Iterable<UserCardSrs> findAllById(Iterable<Long> ids) {
        return em.createQuery("from UserCardSrs where id in :ids", UserCardSrs.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    @Override
    public long count() {
        return em.createQuery("select count(u) from UserCardSrs u", Long.class).getSingleResult();
    }

    @Override
    public void deleteById(Long id) {
        findById(id).ifPresent(em::remove);
    }

    @Override
    public void delete(UserCardSrs entity) {
        em.remove(entity);
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        ids.forEach(this::deleteById);
    }

    @Override
    public void deleteAll(Iterable<? extends UserCardSrs> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        em.createQuery("delete from UserCardSrs").executeUpdate();
    }
}
