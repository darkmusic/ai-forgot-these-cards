package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import jakarta.persistence.TypedQuery;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface CardDAO extends CrudRepository<Card, Long> {
    List<Card> query(TypedQuery<Card> query);
}
