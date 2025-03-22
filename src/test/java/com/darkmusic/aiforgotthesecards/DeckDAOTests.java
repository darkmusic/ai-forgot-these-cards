package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Set;

@SpringBootTest
@TestPropertySource("/application-test.properties")
public class DeckDAOTests {
    @Autowired
    private DeckDAO deckDAO;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private TagDAO tagDAO;

    @Autowired
    private ThemeDAO themeDAO;

    static Deck createDeck(DeckDAO deckDAO, UserDAO userDAO, TagDAO tagDAO, ThemeDAO themeDAO) {
        var user = UserDAOTests.createUser(userDAO, themeDAO);
        var tag = TagDAOTests.createTag(tagDAO);
        var deck = new Deck();
        deck.setName("Test Deck " + System.currentTimeMillis());
        deck.setDescription("This is a test deck");
        deck.setUser(user);
        deck.setTags(Set.of(tag));
        deckDAO.save(deck);
        return deck;
    }

    @Test
    void canCreateDeck() {
        System.out.println("Testing deck creation");
        createDeck(deckDAO, userDAO, tagDAO, themeDAO);
    }
}
