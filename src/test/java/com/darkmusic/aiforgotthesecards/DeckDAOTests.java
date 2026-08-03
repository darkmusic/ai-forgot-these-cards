package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource("/application-test.properties")
public class DeckDAOTests {
    private static final String PRESENTATION_CONFIG_JSON = """
            {
              "scriptStyles": {
                "Arab": { "fontFamily": "'Noto Nastaliq Urdu', serif" }
              }
            }
            """;
    private static final String ALWAYS_APPLIED_TEMPLATE_FRONT = "> Front-only deck note";
    private static final String ALWAYS_APPLIED_TEMPLATE_BACK = "> Back-only deck note";

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

    @Test
    void persistsPresentationConfigJson() {
        Deck deck = createDeck(deckDAO, userDAO, tagDAO, themeDAO);
        deck.setPresentationConfigJson(PRESENTATION_CONFIG_JSON);
        Deck saved = deckDAO.save(deck);

        Deck loaded = deckDAO.findById(saved.getId()).orElseThrow();
        assertEquals(PRESENTATION_CONFIG_JSON, loaded.getPresentationConfigJson());
    }

    @Test
    void persistsAlwaysAppliedTemplates() {
        Deck deck = createDeck(deckDAO, userDAO, tagDAO, themeDAO);
        deck.setAlwaysAppliedTemplateFront(ALWAYS_APPLIED_TEMPLATE_FRONT);
        deck.setAlwaysAppliedTemplateBack(ALWAYS_APPLIED_TEMPLATE_BACK);
        Deck saved = deckDAO.save(deck);

        Deck loaded = deckDAO.findById(saved.getId()).orElseThrow();
        assertEquals(ALWAYS_APPLIED_TEMPLATE_FRONT, loaded.getAlwaysAppliedTemplateFront());
        assertEquals(ALWAYS_APPLIED_TEMPLATE_BACK, loaded.getAlwaysAppliedTemplateBack());
    }
}
