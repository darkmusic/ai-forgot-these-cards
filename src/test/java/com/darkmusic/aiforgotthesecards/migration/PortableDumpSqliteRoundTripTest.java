package com.darkmusic.aiforgotthesecards.migration;

import com.darkmusic.aiforgotthesecards.business.entities.AiChat;
import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.Tag;
import com.darkmusic.aiforgotthesecards.business.entities.Theme;
import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.UserCardSrs;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.AiChatDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.CardDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.DeckDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.TagDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.ThemeDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserCardSrsDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        properties = {
                "DB_VENDOR=sqlite",
                "SQLITE_DB_PATH=target/test-sqlite/portable-roundtrip.db",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
class PortableDumpSqliteRoundTripTest {

    private static final Path SQLITE_PATH = Path.of("target/test-sqlite/portable-roundtrip.db");
    private static final Path ZIP_PATH = Path.of("target/test-sqlite/portable-roundtrip.zip");

    @BeforeAll
    static void cleanup() throws Exception {
        Files.createDirectories(SQLITE_PATH.getParent());
        Files.deleteIfExists(SQLITE_PATH);
        Files.deleteIfExists(ZIP_PATH);
    }

        @Autowired
        private DataSource dataSource;

        @Autowired
        private ThemeDAO themeDAO;

        @Autowired
        private UserDAO userDAO;

        @Autowired
        private DeckDAO deckDAO;

        @Autowired
        private TagDAO tagDAO;

        @Autowired
        private CardDAO cardDAO;

        @Autowired
        private UserCardSrsDAO userCardSrsDAO;

        @Autowired
        private AiChatDAO aiChatDAO;

        @Test
        void exportThenImportRoundTrip() throws Exception {

        Theme theme = new Theme();
        theme.setName("Default");
        theme.setDescription("Test theme");
        theme.setCssUrl("/theme.css");
        theme.setActive(true);
        themeDAO.save(theme);

        User user = new User();
        user.setUsername("alice");
        user.setName("Alice");
        user.setPassword_hash("bcrypt$dummy");
        user.setAdmin(false);
        user.setActive(true);
        user.setProfile_pic_url("/vite.svg");
        user.setThemeId(theme.getId());
        userDAO.save(user);

        Tag tag = new Tag();
        tag.setName("tag1");
        tagDAO.save(tag);

        Deck deck = new Deck();
        deck.setName("Deck 1");
        deck.setDescription("Desc");
        deck.setUser(user);
        deck.setTags(Set.of(tag));
        deckDAO.save(deck);

        Card card = new Card();
        card.setDeck(deck);
        card.setFront("front");
        card.setBack("back");
        card.setTags(Set.of(tag));
        cardDAO.save(card);

        UserCardSrs srs = new UserCardSrs();
        srs.setUser(user);
        srs.setCard(card);
        srs.setNextReviewAt(LocalDateTime.now().plusDays(1));
        srs.setIntervalDays(1);
        srs.setEaseFactor(2.5f);
        srs.setRepetitions(1);
        srs.setLastReviewedAt(LocalDateTime.now());
        userCardSrsDAO.save(srs);

        AiChat chat = new AiChat();
        chat.setUser(user);
        chat.setQuestion("q");
        chat.setAnswer("a");
        chat.setCreatedAt(System.currentTimeMillis());
        aiChatDAO.save(chat);

        long themesBefore = themeDAO.count();
        long usersBefore = userDAO.count();
        long decksBefore = deckDAO.count();
        long tagsBefore = tagDAO.count();
        long cardsBefore = cardDAO.count();
        long srsBefore = userCardSrsDAO.count();
        long chatsBefore = aiChatDAO.count();
        long deckTagsBefore;
        long cardTagsBefore;
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            deckTagsBefore = countRows(st, "deck_tag");
            cardTagsBefore = countRows(st, "card_tag");
        }

        PortableDumpService svc = new PortableDumpService(dataSource);
        svc.exportTo(ZIP_PATH);

        // Truncate and import back into the same DB.
        svc.importFrom(ZIP_PATH, PortableDumpService.ImportMode.TRUNCATE);

        assertEquals(themesBefore, themeDAO.count());
        assertEquals(usersBefore, userDAO.count());
        assertEquals(decksBefore, deckDAO.count());
        assertEquals(tagsBefore, tagDAO.count());
        assertEquals(cardsBefore, cardDAO.count());
        assertEquals(srsBefore, userCardSrsDAO.count());
        assertEquals(chatsBefore, aiChatDAO.count());

        User loaded = userDAO.findByUsername("alice").orElseThrow();
        assertNotNull(loaded.getId());

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            assertEquals(deckTagsBefore, countRows(st, "deck_tag"));
            assertEquals(cardTagsBefore, countRows(st, "card_tag"));
        }
    }

    private static long countRows(Statement st, String table) throws Exception {
        try (ResultSet rs = st.executeQuery("select count(*) from \"" + table + "\"");) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
