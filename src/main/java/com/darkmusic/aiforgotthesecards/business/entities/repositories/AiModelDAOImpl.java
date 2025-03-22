package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.AiModel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

public class AiModelDAOImpl implements AiModelDAO {
    private final EntityManager em;

    public AiModelDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<AiModel> query(TypedQuery<AiModel> query) {
        return query.getResultList();
    }

    @Override
    public AiModel findAiModelByModel(String model) {
        return em.createQuery("from AiModel where model = :model", AiModel.class)
                .setParameter("model", model)
                .getSingleResult();
    }

    @Override
    public <S extends AiModel> S save(S entity) {
        if (entity.getId() != null && entity.getId() > 0L) {
            em.merge(entity);
        }
        else {
            em.persist(entity);
        }

        return entity;
    }

    @Override
    public <S extends AiModel> Iterable<S> saveAll(Iterable<S> entities) {
        for (S entity : entities) {
            save(entity);
        }
        return entities;
    }

    @Override
    public Optional<AiModel> findById(Long aLong) {
        return Optional.ofNullable(em.find(AiModel.class, aLong));
    }

    @Override
    public boolean existsById(Long aLong) {
        return findById(aLong) != null;
    }

    @Override
    public Iterable<AiModel> findAll() {
        return em.createQuery("from AiModel", AiModel.class).getResultList();
    }

    @Override
    public Iterable<AiModel> findAllById(Iterable<Long> longs) {
        return em.createQuery("from AiModel where id in :ids", AiModel.class).setParameter("ids", longs).getResultList();
    }

    @Override
    public long count() {
        return em.createQuery("select count(*) from AiModel", Integer.class).getSingleResult();
    }

    @Override
    public void deleteById(Long aLong) {
        em.remove(em.find(AiModel.class, aLong));
    }

    @Override
    public void delete(AiModel entity) {
        em.remove(em.merge(entity));
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> longs) {
        for (Long id : longs) {
            em.remove(em.find(AiModel.class, id));
        }
    }

    @Override
    public void deleteAll(Iterable<? extends AiModel> entities) {
        for (AiModel entity : entities) {
            em.remove(em.merge(entity));
        }
    }

    @Override
    public void deleteAll() {
        em.createQuery("delete from AiModel").executeUpdate();
    }
}
