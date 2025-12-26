package com.darkmusic.aiforgotthesecards.web.controller;

import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.Tag;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.DeckDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.TagDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import lombok.Getter;
import org.springframework.web.bind.annotation.*;

@Getter
@RestController
public class TagController {
    private final DeckDAO deckDAO;
    private final UserDAO userDAO;
    private final TagDAO tagDAO;

    public TagController(DeckDAO deckDAO, UserDAO userDAO, TagDAO tagDAO) {
        this.deckDAO = deckDAO;
        this.userDAO = userDAO;
        this.tagDAO = tagDAO;
    }

    @GetMapping("/api/tag/{id}")
    public Tag getTag(@PathVariable long id) {
        return tagDAO.findById(id).orElse(null);
    }

    @GetMapping("/api/tag/user/{userId}")
    public Iterable<Deck> getTagsByUser(@PathVariable long userId) {
        var user = userDAO.findById(userId).orElse(null);
        return deckDAO.findByUser(user);
    }

    @GetMapping("/api/tag/all")
    public Iterable<Tag> getAllTags() {
        return tagDAO.findAll();
    }

    @GetMapping("/api/tag/used/decks")
    public Iterable<Tag> getTagsUsedByDecks() {
        return tagDAO.findTagsUsedByDecks();
    }

    @GetMapping("/api/tag/used/cards")
    public Iterable<Tag> getTagsUsedByCards() {
        return tagDAO.findTagsUsedByCards();
    }

    @PostMapping("/api/tag")
    public Tag addTag(@RequestBody Tag tag) {
        return tagDAO.save(tag);
    }
}