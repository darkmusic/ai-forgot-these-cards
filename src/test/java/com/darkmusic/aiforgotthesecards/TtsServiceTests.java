package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.TtsAudio;
import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.*;
import com.darkmusic.aiforgotthesecards.business.entities.services.TtsService;
import com.darkmusic.aiforgotthesecards.web.contracts.TtsPlaybackItem;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource("/application-test.properties")
public class TtsServiceTests {
    private static final AtomicInteger SYNTHESIS_CALLS = new AtomicInteger(0);
    private static final HttpServer TTS_SERVER = startServer();

    @Autowired
    private TtsService ttsService;

    @Autowired
    private TtsAudioDAO ttsAudioDAO;

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

    @DynamicPropertySource
    static void ttsProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:ttsdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false");
        registry.add("tts.service-url", () -> "http://localhost:" + TTS_SERVER.getAddress().getPort());
        registry.add("tts.storage-dir", () -> "target/test-tts");
    }

    @AfterAll
    static void stopServer() {
        TTS_SERVER.stop(0);
    }

    @BeforeEach
    void resetServerCounter() throws IOException {
        SYNTHESIS_CALLS.set(0);
        Path storage = Path.of("target/test-tts");
        if (Files.exists(storage)) {
            try (var files = Files.walk(storage)) {
                files.sorted(Comparator.reverseOrder())
                        .filter(path -> !path.equals(storage))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        }
    }

    @Test
    void persistsDeckCardSemanticAudioMetadata() throws Exception {
        Fixture fixture = createTtsFixture();
        var response = ttsService.generateForCard(fixture.card.getId(), "word", "hindi", fixture.user);

        TtsAudio audio = ttsAudioDAO.findById(response.getId()).orElseThrow();
        assertEquals(fixture.deck.getId(), audio.getDeck().getId());
        assertEquals(fixture.card.getId(), audio.getCard().getId());
        assertEquals("word", audio.getTarget());
        assertEquals("hindi", audio.getVariant());
        assertEquals("hi", audio.getLanguage());
        assertEquals("devanagari", audio.getTextSource());
        assertEquals("नमस्ते", audio.getResolvedText());
        assertTrue(Files.exists(Path.of(audio.getFilePath())));
    }

    @Test
    void rejectsMissingDeckTtsConfig() {
        var card = CardDAOTests.createCard(cardDAO, deckDAO, userDAO, tagDAO, themeDAO);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> ttsService.generateForCard(card.getId(), null, null, card.getDeck().getUser())
        );
        assertEquals(400, ex.getStatusCode().value());
        assertEquals(0, SYNTHESIS_CALLS.get());
    }

    @Test
    void cacheHitSkipsSidecarCall() throws Exception {
        Fixture fixture = createTtsFixture();

        var first = ttsService.generateForCard(fixture.card.getId(), "word", "hindi", fixture.user);
        var second = ttsService.generateForCard(fixture.card.getId(), "word", "hindi", fixture.user);

        assertFalse(first.isCached());
        assertTrue(second.isCached());
        assertEquals(first.getId(), second.getId());
        assertEquals(1, SYNTHESIS_CALLS.get());
    }

    @Test
    void forceRegenerateBypassesCache() throws Exception {
        Fixture fixture = createTtsFixture();

        var first = ttsService.generateForCard(fixture.card.getId(), "word", "hindi", fixture.user);
        Thread.sleep(5);
        var regenerated = ttsService.generateForCard(
                fixture.card.getId(), "word", "hindi", fixture.user, true, message -> {});

        assertFalse(first.isCached());
        assertFalse(regenerated.isCached());
        assertEquals(first.getId(), regenerated.getId());
        assertEquals(2, SYNTHESIS_CALLS.get());
        assertNotEquals(first.getGeneratedAt(), regenerated.getGeneratedAt());
    }

    @Test
    void resolvesBackSideWordAndExampleControlsForBothVariants() throws Exception {
        Fixture fixture = createTtsFixture("""
                {
                  "provider": "indic-parler-tts",
                  "targets": {
                    "word": { "enabled": true, "displaySide": "BACK", "voices": ["hindi", "urdu"] },
                    "example": { "enabled": true, "displaySide": "BACK", "voices": ["hindi", "urdu"] }
                  },
                  "variants": {
                    "hindi": {
                      "language": "hi",
                      "textSource": "devanagari",
                      "caption": "Hindi",
                      "speaker": "Divya",
                      "generationConfig": {}
                    },
                    "urdu": {
                      "language": "ur",
                      "textSource": "urduScript",
                      "caption": "Urdu",
                      "speaker": "",
                      "generationConfig": {}
                    }
                  },
                  "defaultVariant": "hindi",
                  "showVariantControls": "both"
                }
                """, """
                - Devanagari: नमस्ते
                - Urdu script: سلام
                """, """
                - Example sentence:
                - Devanagari: नमस्ते दुनिया
                - Urdu: سلام دنیا
                """);

        var itemsByKey = ttsService.getPlaybackItems(fixture.card.getId(), fixture.user).stream()
                .collect(Collectors.toMap(item -> item.getTarget() + ":" + item.getVariant(), Function.identity()));

        assertEquals(Set.of("word:hindi", "word:urdu", "example:hindi", "example:urdu"), itemsByKey.keySet());
        assertEquals("BACK", itemsByKey.get("word:hindi").getDisplaySide());
        assertEquals("BACK", itemsByKey.get("example:urdu").getDisplaySide());
        assertItem(itemsByKey.get("word:hindi"), "Word Hindi", "devanagari", "नमस्ते");
        assertItem(itemsByKey.get("word:urdu"), "Word Urdu", "urduScript", "سلام");
        assertItem(itemsByKey.get("example:hindi"), "Example Hindi", "example.devanagari", "नमस्ते दुनिया");
        assertItem(itemsByKey.get("example:urdu"), "Example Urdu", "example.urduScript", "سلام دنیا");
    }

    @Test
    void audioStreamingRequiresDeckOwnership() throws Exception {
        Fixture fixture = createTtsFixture();
        var audio = ttsService.generateForCard(fixture.card.getId(), "word", "hindi", fixture.user);
        User otherUser = UserDAOTests.createUser(userDAO, themeDAO);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> ttsService.getOwnedAudio(audio.getId(), otherUser)
        );
        assertEquals(404, ex.getStatusCode().value());
    }

    private Fixture createTtsFixture() {
        return createTtsFixture("""
                {
                  "targets": {
                    "word": { "enabled": true, "displaySide": "BACK", "voices": ["hindi", "urdu"] }
                  },
                  "variants": {
                    "hindi": {
                      "language": "hi",
                      "textSource": "devanagari",
                      "caption": "Leela speaks clearly with a moderate pace.",
                      "speaker": "Leela",
                      "generationConfig": { "do_sample": false }
                    },
                    "urdu": {
                      "language": "ur",
                      "textSource": "urduScript",
                      "caption": "",
                      "speaker": "",
                      "generationConfig": {}
                    }
                  },
                  "defaultVariant": "hindi"
                }
                """, """
                - Devanagari: नमस्ते
                - Urdu script: سلام
                """, "Greeting");
    }

    private Fixture createTtsFixture(String ttsConfigJson, String front, String back) {
        Deck deck = DeckDAOTests.createDeck(deckDAO, userDAO, tagDAO, themeDAO);
        deck.setTtsEnabled(true);
        deck.setTtsModelId("test/model");
        deck.setTtsConfigJson(ttsConfigJson);
        deckDAO.save(deck);

        Card card = new Card();
        card.setDeck(deck);
        card.setFront(front);
        card.setBack(back);
        card.setTags(Set.of(TagDAOTests.createTag(tagDAO)));
        cardDAO.save(card);

        return new Fixture(deck, card, deck.getUser());
    }

    private static void assertItem(TtsPlaybackItem item, String label, String textSource, String text) {
        assertNotNull(item);
        assertEquals(label, item.getLabel());
        assertEquals(textSource, item.getTextSource());
        assertEquals(text, item.getText());
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/synthesize", exchange -> {
                SYNTHESIS_CALLS.incrementAndGet();
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (!requestBody.contains("test/model") || !requestBody.contains("नमस्ते")) {
                    exchange.sendResponseHeaders(400, 0);
                    exchange.close();
                    return;
                }
                byte[] wav = "RIFF$\0\0\0WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1);
                exchange.getResponseHeaders().add("Content-Type", "audio/wav");
                exchange.sendResponseHeaders(200, wav.length);
                exchange.getResponseBody().write(wav);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private record Fixture(Deck deck, Card card, User user) {
    }
}
