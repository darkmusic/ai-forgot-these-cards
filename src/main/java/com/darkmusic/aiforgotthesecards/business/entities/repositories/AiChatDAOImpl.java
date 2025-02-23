package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.AiChat;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

public class AiChatDAOImpl implements AiChatDAO {
    private final EntityManager em;

    public AiChatDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<AiChat> query(TypedQuery<AiChat> query) {
        return query.getResultList();
    }

    @Override
    public <S extends AiChat> S save(S entity) {
        em.persist(entity);
        return entity;
    }

    @Override
    public <S extends AiChat> Iterable<S> saveAll(Iterable<S> entities) {
        for (S entity : entities) {
            em.persist(entity);
        }
        return entities;
    }

    @Override
    public Optional<AiChat> findById(Long aLong) {
        return Optional.ofNullable(em.find(AiChat.class, aLong));
    }

    @Override
    public boolean existsById(Long aLong) {
        return findById(aLong) != null;
    }

    @Override
    public Iterable<AiChat> findAll() {
        return em.createQuery("from AiChat", AiChat.class).getResultList();
    }

    @Override
    public Iterable<AiChat> findAllById(Iterable<Long> longs) {
        return em.createQuery("from AiChat where id in :ids", AiChat.class).setParameter("ids", longs).getResultList();
    }

    @Override
    public long count() {
        return em.createQuery("select count(*) from AiChat", Integer.class).getSingleResult();
    }

    @Override
    public void deleteById(Long aLong) {
        em.remove(em.find(AiChat.class, aLong));
    }

    @Override
    public void delete(AiChat entity) {
        em.remove(em.merge(entity));
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> longs) {
        for (Long id : longs) {
            em.remove(em.find(AiChat.class, id));
        }
    }

    @Override
    public void deleteAll(Iterable<? extends AiChat> entities) {
        for (AiChat entity : entities) {
            em.remove(em.merge(entity));
        }
    }

    @Override
    public void deleteAll() {
        em.createQuery("delete from AiChat").executeUpdate();
    }
}
