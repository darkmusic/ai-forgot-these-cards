package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.Tag;
import jakarta.persistence.TypedQuery;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface TagDAO extends CrudRepository<Tag, Long> {
    List<Tag> query(TypedQuery<Tag> query);

    List<Tag> findTagsUsedByDecks();

    List<Tag> findTagsUsedByCards();
}
