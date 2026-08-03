package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.CardDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.DeckDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.TagDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.ThemeDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import com.darkmusic.aiforgotthesecards.web.controller.SrsController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource("/application-test.properties")
public class SrsControllerTests {
    private static final String PRESENTATION_CONFIG_JSON = """
            {
              "scriptStyles": {
                "Arab": { "fontFamily": "'Noto Nastaliq Urdu', serif" },
                "Deva": { "fontFamily": "'Noto Sans Devanagari', sans-serif" }
              }
            }
            """;
    private static final String ALWAYS_APPLIED_TEMPLATE_FRONT = "> Front-only deck note";
    private static final String ALWAYS_APPLIED_TEMPLATE_BACK = "> Back-only deck note";

    @Autowired
    private SrsController srsController;

    @Autowired
    private CardDAO cardDAO;

    @Autowired
    private DeckDAO deckDAO;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private TagDAO tagDAO;

    @Autowired
    private ThemeDAO themeDAO;

    @Test
    void deckInfoIncludesPresentationConfigInReviewAndCramQueues() {
        var card = CardDAOTests.createCard(cardDAO, deckDAO, userDAO, tagDAO, themeDAO);
        Deck deck = card.getDeck();
        deck.setPresentationConfigJson(PRESENTATION_CONFIG_JSON);
        deck.setAlwaysAppliedTemplateFront(ALWAYS_APPLIED_TEMPLATE_FRONT);
        deck.setAlwaysAppliedTemplateBack(ALWAYS_APPLIED_TEMPLATE_BACK);
        deckDAO.save(deck);
        var authentication = new TestingAuthenticationToken(deck.getUser().getUsername(), "password");

        var reviewQueue = srsController.getReviewQueue(authentication, deck.getId());
        var cramQueue = srsController.getCramQueue(authentication, deck.getId());

        assertEquals(PRESENTATION_CONFIG_JSON, reviewQueue.getFirst().getDeck().getPresentationConfigJson());
        assertEquals(PRESENTATION_CONFIG_JSON, cramQueue.getFirst().getDeck().getPresentationConfigJson());
        assertEquals(ALWAYS_APPLIED_TEMPLATE_FRONT, reviewQueue.getFirst().getDeck().getAlwaysAppliedTemplateFront());
        assertEquals(ALWAYS_APPLIED_TEMPLATE_BACK, reviewQueue.getFirst().getDeck().getAlwaysAppliedTemplateBack());
        assertEquals(ALWAYS_APPLIED_TEMPLATE_FRONT, cramQueue.getFirst().getDeck().getAlwaysAppliedTemplateFront());
        assertEquals(ALWAYS_APPLIED_TEMPLATE_BACK, cramQueue.getFirst().getDeck().getAlwaysAppliedTemplateBack());
    }
}
