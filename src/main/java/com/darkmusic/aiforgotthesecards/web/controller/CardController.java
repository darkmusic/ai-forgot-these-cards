package com.darkmusic.aiforgotthesecards.web.controller;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.CardDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.DeckDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.TagDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import lombok.Getter;
import org.springframework.web.bind.annotation.*;

@Getter
@RestController
public class CardController {
    private final CardDAO cardDAO;
    private final DeckDAO deckDAO;
    private final UserDAO userDAO;
    private final TagDAO tagDAO;

    public CardController(CardDAO cardDAO, DeckDAO deckDAO, UserDAO userDAO, TagDAO tagDAO) {
        this.cardDAO = cardDAO;
        this.deckDAO = deckDAO;
        this.userDAO = userDAO;
        this.tagDAO = tagDAO;
    }

    @GetMapping("/api/card/{id}")
    public Card getCard(@PathVariable long id) {
        return cardDAO.findById(id).orElse(null);
    }

    @GetMapping("/api/card/deck/{deckId}")
    public Iterable<Card> getCardsByDeck(@PathVariable long deckId) {
        var deck = deckDAO.findById(deckId).orElse(null);
        return cardDAO.findByDeck(deck);
    }

    @GetMapping("/api/card/user/{userId}")
    public Iterable<Card> getCardsByUser(@PathVariable long userId) {
        var user = userDAO.findById(userId).orElse(null);
        return cardDAO.findByDeckUser(user);
    }

    @GetMapping("/api/card/all")
    public Iterable<Card> getAllCards() {
        return cardDAO.findAll();
    }

    @PostMapping("/api/card")
    public Card addCard(@RequestBody Card card) {
        return cardDAO.save(card);
    }

    @PutMapping("/api/card/{id}")
    public void saveCard(@PathVariable long id, @RequestBody Card card) {
        if (cardDAO.findById(id).isPresent()) {
            cardDAO.save(card);
        }
    }

    @DeleteMapping("/api/card/{id}")
    public void deleteCard(@PathVariable long id) {
        cardDAO.deleteById(id);
    }

    @GetMapping("/api/card/username/{username}")
    public Iterable<Card> getCardsByUsername(@PathVariable String username) {
        var user = userDAO.findByUsername(username).orElse(null);
        if (user != null) {
            return cardDAO.findByDeckUser(user);
        }
        return null;
    }
}
