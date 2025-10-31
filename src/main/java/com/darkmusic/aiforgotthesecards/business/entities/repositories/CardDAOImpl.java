package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class CardDAOImpl implements CardDAO {
    private final EntityManager em;

    public CardDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<Card> query(TypedQuery<Card> query) {
        return query.getResultList();
    }

    @Override
    public Iterable<Card> findByDeck(Deck deck) {
        return em.createQuery("from Card c join fetch c.deck where c.deck.id = :deckId", Card.class)
                .setParameter("deckId", deck.getId())
                .getResultList();
    }

    @Override
    public Iterable<Card> findByDeckUser(User user) {
        return em.createQuery("from Card c join fetch c.deck where c.deck.user.id = :userId", Card.class)
                .setParameter("userId", user.getId())
                .getResultList();
    }

    @Override
    public long countByDeckUser(User user) {
        return em.createQuery("select count(c) from Card c where c.deck.user.id = :userId", Long.class)
                .setParameter("userId", user.getId())
                .getSingleResult();
    }

    @Override
    public <S extends Card> S save(S entity) {
        if (entity.getId() != null && entity.getId() > 0L) {
            em.merge(entity);
        }
        else {
            em.persist(entity);
        }

        return entity;
    }

    @Override
    public <S extends Card> Iterable<S> saveAll(Iterable<S> entities) {
        for (S entity : entities) {
            save(entity);
        }
        return entities;
    }

    @Override
    public Optional<Card> findById(Long aLong) {
        return Optional.ofNullable(em.find(Card.class, aLong));
    }

    @Override
    public boolean existsById(Long aLong) {
        return findById(aLong) != null;
    }

    @Override
    public Iterable<Card> findAll() {
        return em.createQuery("from Card", Card.class).getResultList();
    }

    @Override
    public Iterable<Card> findAllById(Iterable<Long> longs) {
        return em.createQuery("from Card where id in :ids", Card.class).setParameter("ids", longs).getResultList();
    }

    @Override
    public long count() {
        return em.createQuery("select count(*) from Card", Integer.class).getSingleResult();
    }

    @Override
    public void deleteById(Long aLong) {
        em.remove(em.find(Card.class, aLong));
    }

    @Override
    public void delete(Card entity) {
        em.remove(em.merge(entity));
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> longs) {
        for (Long id : longs) {
            em.remove(em.find(Card.class, id));
        }
    }

    @Override
    public void deleteAll(Iterable<? extends Card> entities) {
        for (Card entity : entities) {
            em.remove(em.merge(entity));
        }
    }

    @Override
    public void deleteAll() {
        em.createQuery("delete from Card").executeUpdate();
    }
}
