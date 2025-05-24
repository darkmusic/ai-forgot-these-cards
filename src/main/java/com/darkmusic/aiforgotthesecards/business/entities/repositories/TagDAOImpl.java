package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.Tag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class TagDAOImpl implements TagDAO {
    private final EntityManager em;

    public TagDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<Tag> query(TypedQuery<Tag> query) {
        return query.getResultList();
    }

    @Override
    public <S extends Tag> S save(S entity) {
        if (entity.getId() != null && entity.getId() > 0L) {
            em.merge(entity);
        }
        else {
            em.persist(entity);
        }
        return entity;
    }

    @Override
    public <S extends Tag> Iterable<S> saveAll(Iterable<S> entities) {
        for (S entity : entities) {
            save(entity);
        }
        return entities;
    }

    @Override
    public Optional<Tag> findById(Long aLong) {
        return Optional.ofNullable(em.find(Tag.class, aLong));
    }

    @Override
    public boolean existsById(Long aLong) {
        return findById(aLong) != null;
    }

    @Override
    public Iterable<Tag> findAll() {
        return em.createQuery("from Tag", Tag.class).getResultList();
    }

    @Override
    public Iterable<Tag> findAllById(Iterable<Long> longs) {
        return em.createQuery("from Tag where id in :ids", Tag.class).setParameter("ids", longs).getResultList();
    }

    @Override
    public long count() {
        return em.createQuery("select count(*) from Tag", Integer.class).getSingleResult();
    }

    @Override
    public void deleteById(Long aLong) {
        em.remove(em.find(Tag.class, aLong));
    }

    @Override
    public void delete(Tag entity) {
        em.remove(em.merge(entity));
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> longs) {
        for (Long id : longs) {
            em.remove(em.find(Tag.class, id));
        }
    }

    @Override
    public void deleteAll(Iterable<? extends Tag> entities) {
        for (Tag entity : entities) {
            em.remove(em.merge(entity));
        }
    }

    @Override
    public void deleteAll() {
        em.createQuery("delete from Tag").executeUpdate();
    }
}
