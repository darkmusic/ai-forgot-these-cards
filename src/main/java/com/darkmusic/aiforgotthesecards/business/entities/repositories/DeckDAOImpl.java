package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class DeckDAOImpl implements DeckDAO {
    private final EntityManager em;

    public DeckDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<Deck> query(TypedQuery<Deck> query) {
        return query.getResultList();
    }

    @Override
    public Iterable<Deck> findByUser(User user) {
        return em.createQuery("from Deck where user_id = :userId", Deck.class)
                .setParameter("userId", user.getId())
                .getResultList();
    }

    @Override
    public <S extends Deck> S save(S entity) {
        if (entity.getId() != null && entity.getId() > 0L) {
            em.merge(entity);
        }
        else {
            em.persist(entity);
        }
        return entity;
    }

    @Override
    public <S extends Deck> Iterable<S> saveAll(Iterable<S> entities) {
        for (S entity : entities) {
            save(entity);
        }
        return entities;
    }

    @Override
    public Optional<Deck> findById(Long aLong) {
        return Optional.ofNullable(em.find(Deck.class, aLong));
    }

    @Override
    public boolean existsById(Long aLong) {
        return findById(aLong).isPresent();
    }

    @Override
    public Iterable<Deck> findAll() {
        return em.createQuery("from Deck", Deck.class).getResultList();
    }

    @Override
    public Iterable<Deck> findAllById(Iterable<Long> longs) {
        return em.createQuery("from Deck where id in :ids", Deck.class).setParameter("ids", longs).getResultList();
    }

    @Override
    public long count() {
        return em.createQuery("select count(*) from Deck", Long.class).getSingleResult();
    }

    @Override
    public void deleteById(Long aLong) {
        em.remove(em.find(Deck.class, aLong));
    }

    @Override
    public void delete(Deck entity) {
        em.remove(em.merge(entity));
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> longs) {
        for (Long id : longs) {
            em.remove(em.find(Deck.class, id));
        }
    }

    @Override
    public void deleteAll(Iterable<? extends Deck> entities) {
        for (Deck entity : entities) {
            em.remove(em.merge(entity));
        }
    }

    @Override
    public void deleteAll() {
        em.createQuery("delete from Deck").executeUpdate();
    }
}
