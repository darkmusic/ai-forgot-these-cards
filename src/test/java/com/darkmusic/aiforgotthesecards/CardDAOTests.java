package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Set;

@SpringBootTest
@TestPropertySource("/application-test.properties")
public class CardDAOTests {
    @Autowired
    private CardDAO cardDAO;

    @Autowired
    private DeckDAO deckDAO;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private TagDAO tagDAO;

    static Card createCard(CardDAO cardDAO, DeckDAO deckDAO, UserDAO userDAO, TagDAO tagDAO) {
        var deck = DeckDAOTests.createDeck(deckDAO, userDAO, tagDAO);
        var tag = TagDAOTests.createTag(tagDAO);
        var card = new Card();
        card.setFront("Test Front " + System.currentTimeMillis());
        card.setBack("Test Back " + System.currentTimeMillis());
        card.setDeck(deck);
        card.setTags(Set.of(tag));
        cardDAO.save(card);
        return card;
    }

    @Test
    void canCreateCard() {
        System.out.println("Testing card creation");
        createCard(cardDAO, deckDAO, userDAO, tagDAO);
    }
}
