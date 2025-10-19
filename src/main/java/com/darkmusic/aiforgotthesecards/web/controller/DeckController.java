package com.darkmusic.aiforgotthesecards.web.controller;

import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.DeckDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import lombok.Getter;
import org.springframework.web.bind.annotation.*;

@Getter
@RestController
public class DeckController {
    private final DeckDAO deckDAO;
    private final UserDAO userDAO;

    public DeckController(DeckDAO deckDAO, UserDAO userDAO) {
        this.deckDAO = deckDAO;
        this.userDAO = userDAO;
    }

    @GetMapping("/api/deck/{id}")
    public Deck getDeck(@PathVariable long id) {
        return deckDAO.findById(id).orElse(null);
    }

    @GetMapping("/api/deck/user/{userId}")
    public Iterable<Deck> getDecksByUser(@PathVariable long userId) {
        var user = userDAO.findById(userId).orElse(null);
        return deckDAO.findByUser(user);
    }

    @GetMapping("/api/deck/all")
    public Iterable<Deck> getAllDecks() {
        return deckDAO.findAll();
    }

    @PostMapping("/api/deck")
    public Deck addDeck(@RequestBody Deck deck) {
        return deckDAO.save(deck);
    }

    @PutMapping("/api/deck/{id}")
    public Deck saveDeck(@PathVariable long id, @RequestBody Deck deck) {
        if (deckDAO.findById(id).isPresent()) {
            return deckDAO.save(deck);
        }
        return null;
    }

    @DeleteMapping("/api/deck/{id}")
    public void deleteDeck(@PathVariable long id) {
        deckDAO.deleteById(id);
    }

    @GetMapping("/api/deck/username/{username}")
    public Iterable<Deck> getDecksByUsername(@PathVariable String username) {
        var user = userDAO.findByUsername(username).orElse(null);
        if (user != null) {
            return deckDAO.findByUser(user);
        }
        return null;
    }
}