package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.User;
import jakarta.persistence.TypedQuery;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface CardDAO extends CrudRepository<Card, Long> {
    List<Card> query(TypedQuery<Card> query);

    Iterable<Card> findByDeck(Deck deck);

    Iterable<Card> findByDeckWithTags(Deck deck);

    Iterable<Card> findByDeckUser(User user);

    Iterable<Card> findByDeckUserWithTags(User user);

    long countByDeckUser(User user);
}
