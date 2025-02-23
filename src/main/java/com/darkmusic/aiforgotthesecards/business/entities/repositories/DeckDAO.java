package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import jakarta.persistence.TypedQuery;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface DeckDAO extends CrudRepository<Deck, Long> {
    List<Deck> query(TypedQuery<Deck> query);
}
