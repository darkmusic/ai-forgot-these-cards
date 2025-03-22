package com.darkmusic.aiforgotthesecards.web.controller;

import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.DeckDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Getter
@RestController
public class TagController {
    private final DeckDAO deckDAO;
    private final UserDAO userDAO;

    @Autowired
    public TagController(DeckDAO deckDAO, UserDAO userDAO) {
        this.deckDAO = deckDAO;
        this.userDAO = userDAO;
    }

    @GetMapping("/api/tag/{id}")
    public Deck getTag(@PathVariable long id) {
        return deckDAO.findById(id).orElse(null);
    }

    @GetMapping("/api/tag/user/{userId}")
    public Iterable<Deck> getTagsByUser(@PathVariable long userId) {
        var user = userDAO.findById(userId).orElse(null);
        return deckDAO.findByUser(user);
    }

    @GetMapping("/api/tag/all")
    public Iterable<Deck> getAllTags() {
        return deckDAO.findAll();
    }
}