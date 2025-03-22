package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.Theme;
import jakarta.persistence.EntityManager;
import java.util.Optional;

public class ThemeDAOImpl implements ThemeDAO {
    private final EntityManager em;

    public ThemeDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public <S extends Theme> S save(S entity) {
        if (entity.getId() != null && entity.getId() > 0L) {
            em.merge(entity);
        }
        else {
            em.persist(entity);
        }

        return entity;
    }

    @Override
    public <S extends Theme> Iterable<S> saveAll(Iterable<S> entities) {
        for (S entity : entities) {
            save(entity);
        }
        return entities;
    }

    @Override
    public Optional<Theme> findById(Long aLong) {
        return Optional.ofNullable(em.find(Theme.class, aLong));
    }

    @Override
    public boolean existsById(Long aLong) {
        return findById(aLong).isPresent();
    }

    @Override
    public Iterable<Theme> findAll() {
        return em.createQuery("from Theme", Theme.class).getResultList();
    }

    @Override
    public Iterable<Theme> findAllById(Iterable<Long> longs) {
        return em.createQuery("from Theme where id in :ids", Theme.class)
                .setParameter("ids", longs)
                .getResultList();
    }

    @Override
    public long count() {
        return em.createQuery("select count(t) from Theme t", Long.class).getSingleResult();
    }

    @Override
    public void deleteById(Long aLong) {
        em.remove(em.find(Theme.class, aLong));
    }

    @Override
    public void delete(Theme entity) {
        em.remove(em.merge(entity));
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> longs) {
        for (Long id : longs) {
            deleteById(id);
        }
    }

    @Override
    public void deleteAll(Iterable<? extends Theme> entities) {
        for (Theme entity : entities) {
            em.remove(em.merge(entity));
        }
    }

    @Override
    public void deleteAll() {
        em.createQuery("delete from Theme").executeUpdate();
    }
}
