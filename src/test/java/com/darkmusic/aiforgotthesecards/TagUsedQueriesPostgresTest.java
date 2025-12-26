package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.Tag;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.CardDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.DeckDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.TagDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.ThemeDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public class TagUsedQueriesPostgresTest {

    private static final Logger log = LoggerFactory.getLogger(TagUsedQueriesPostgresTest.class);

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("cards")
            .withUsername("cards")
            .withPassword("cards");

    @BeforeAll
    static void logContainerDetails() {
        var info = postgres.getContainerInfo();
        String containerName = info != null ? info.getName() : "<unknown>";
        log.warn(
                "Testcontainers Postgres started: name={}, id={}, jdbcUrl={}, host={}, port={}",
                containerName,
                postgres.getContainerId(),
                postgres.getJdbcUrl(),
                postgres.getHost(),
                postgres.getMappedPort(5432));
    }

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);

        // Ensure Hibernate generates schema for the container DB.
        // NOTE: We intentionally avoid create-drop here. Hibernate performs the DROP during
        // Spring shutdown, which can block if the DataSource is closing, causing Surefire to
        // wait and eventually kill the forked JVM.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");

        // Avoid running init scripts intended for H2.
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private TagDAO tagDAO;

    @Autowired
    private DeckDAO deckDAO;

    @Autowired
    private CardDAO cardDAO;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private ThemeDAO themeDAO;

    @Test
    void usedTagQueriesWorkOnPostgresAndAreCaseInsensitiveSorted() {
        // Deck-used tags
        Tag deckTagB = new Tag();
        deckTagB.setName("b-deck");
        tagDAO.save(deckTagB);

        Tag deckTagA = new Tag();
        deckTagA.setName("A-deck");
        tagDAO.save(deckTagA);

        var user = UserDAOTests.createUser(userDAO, themeDAO);

        Deck deck = new Deck();
        deck.setName("Test Deck " + System.currentTimeMillis());
        deck.setDescription("Test");
        deck.setUser(user);
        deck.setTags(Set.of(deckTagB, deckTagA));
        deckDAO.save(deck);

        // Card-used tags
        Tag cardTagB = new Tag();
        cardTagB.setName("b-card");
        tagDAO.save(cardTagB);

        Tag cardTagA = new Tag();
        cardTagA.setName("A-card");
        tagDAO.save(cardTagA);

        Card card = new Card();
        card.setFront("Front " + System.currentTimeMillis());
        card.setBack("Back " + System.currentTimeMillis());
        card.setDeck(deck);
        card.setTags(Set.of(cardTagB, cardTagA));
        cardDAO.save(card);

        List<Tag> usedByDecks = tagDAO.findTagsUsedByDecks();
        assertTrue(usedByDecks.stream().anyMatch(t -> "A-deck".equals(t.getName())));
        assertTrue(usedByDecks.stream().anyMatch(t -> "b-deck".equals(t.getName())));

        // Verify order by lower(name)
        List<String> deckNames = usedByDecks.stream()
                .map(Tag::getName)
                .filter(n -> n.equals("A-deck") || n.equals("b-deck"))
                .toList();
        assertEquals(List.of("A-deck", "b-deck"), deckNames);

        List<Tag> usedByCards = tagDAO.findTagsUsedByCards();
        assertTrue(usedByCards.stream().anyMatch(t -> "A-card".equals(t.getName())));
        assertTrue(usedByCards.stream().anyMatch(t -> "b-card".equals(t.getName())));

        List<String> cardNames = usedByCards.stream()
                .map(Tag::getName)
                .filter(n -> n.equals("A-card") || n.equals("b-card"))
                .toList();
        assertEquals(List.of("A-card", "b-card"), cardNames);
    }
}
