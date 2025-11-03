package com.darkmusic.aiforgotthesecards.web.controller;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.Tag;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.CardDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.DeckDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.TagDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import com.darkmusic.aiforgotthesecards.web.contracts.BulkCardItem;
import com.darkmusic.aiforgotthesecards.web.contracts.BulkSaveCardsRequest;
import com.darkmusic.aiforgotthesecards.web.contracts.BulkSaveCardsResponse;
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
    public Card saveCard(@PathVariable long id, @RequestBody Card card) {
        if (cardDAO.findById(id).isPresent()) {
            return cardDAO.save(card);
        }
        return null;
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

    // -------- BULK SAVE --------
    @PostMapping("/api/card/bulk-save")
    public BulkSaveCardsResponse bulkSave(@RequestBody BulkSaveCardsRequest req) {
        int created = 0, updated = 0, deleted = 0;

        // Preload all tags into a map by normalized name
        var allTags = tagDAO.findAll();
        java.util.Map<String, Tag> tagByName = new java.util.HashMap<>();
        for (Tag t : allTags) {
            if (t.getName() != null) tagByName.put(normalize(t.getName()), t);
        }

        java.util.function.Function<BulkCardItem, java.util.Set<Tag>> mapTags = (item) -> {
            java.util.Set<Tag> set = new java.util.HashSet<>();
            if (item.getTags() != null) {
                for (String name : item.getTags()) {
                    String n = normalize(name);
                    if (n.isEmpty()) continue;
                    Tag t = tagByName.get(n);
                    if (t == null) {
                        t = new Tag();
                        t.setName(name.trim());
                        tagDAO.save(t);
                        tagByName.put(n, t);
                    }
                    set.add(t);
                }
            }
            return set;
        };

        // Deletes first
        if (req.getDeleteIds() != null) {
            for (Long id : req.getDeleteIds()) {
                if (id != null) {
                    cardDAO.deleteById(id);
                    deleted++;
                }
            }
        }

        // Updates
        if (req.getUpdate() != null) {
            for (BulkCardItem item : req.getUpdate()) {
                if (item.getId() == null) continue;
                var cardOpt = cardDAO.findById(item.getId());
                if (cardOpt.isPresent()) {
                    Card c = cardOpt.get();
                    c.setFront(item.getFront());
                    c.setBack(item.getBack());
                    if (item.getDeckId() != null) {
                        Deck d = deckDAO.findById(item.getDeckId()).orElse(null);
                        if (d != null) c.setDeck(d);
                    }
                    c.setTags(mapTags.apply(item));
                    cardDAO.save(c);
                    updated++;
                }
            }
        }

        // Creates
        if (req.getCreate() != null) {
            for (BulkCardItem item : req.getCreate()) {
                Card c = new Card();
                c.setFront(item.getFront());
                c.setBack(item.getBack());
                Deck d = deckDAO.findById(item.getDeckId()).orElse(null);
                c.setDeck(d);
                c.setTags(mapTags.apply(item));
                cardDAO.save(c);
                created++;
            }
        }

        BulkSaveCardsResponse res = new BulkSaveCardsResponse();
        res.setCreated(created);
        res.setUpdated(updated);
        res.setDeleted(deleted);
        return res;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
