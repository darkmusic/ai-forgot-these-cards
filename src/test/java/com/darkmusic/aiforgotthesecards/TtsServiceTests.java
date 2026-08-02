package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.TtsAudio;
import com.darkmusic.aiforgotthesecards.business.entities.TtsPreset;
import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.*;
import com.darkmusic.aiforgotthesecards.business.entities.services.TtsService;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource("/application-test.properties")
public class TtsServiceTests {
    private static final AtomicInteger SYNTHESIS_CALLS = new AtomicInteger(0);
    private static final HttpServer TTS_SERVER = startServer();

    @Autowired
    private TtsService ttsService;

    @Autowired
    private TtsPresetDAO ttsPresetDAO;

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
    void persistsDeckCardPresetAndAudioMetadata() throws Exception {
        Fixture fixture = createTtsFixture();
        var response = ttsService.generateForCard(fixture.card.getId(), fixture.user);

        TtsAudio audio = ttsAudioDAO.findById(response.getId()).orElseThrow();
        assertEquals(fixture.deck.getId(), audio.getDeck().getId());
        assertEquals(fixture.card.getId(), audio.getCard().getId());
        assertEquals(fixture.preset.getId(), audio.getPreset().getId());
        assertTrue(Files.exists(Path.of(audio.getFilePath())));
    }

    @Test
    void rejectsMissingDeckTtsConfig() {
        var card = CardDAOTests.createCard(cardDAO, deckDAO, userDAO, tagDAO, themeDAO);
        card.setTtsText("hello");
        cardDAO.save(card);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> ttsService.generateForCard(card.getId(), card.getDeck().getUser())
        );
        assertEquals(400, ex.getStatusCode().value());
        assertEquals(0, SYNTHESIS_CALLS.get());
    }

    @Test
    void cacheHitSkipsSidecarCall() throws Exception {
        Fixture fixture = createTtsFixture();

        var first = ttsService.generateForCard(fixture.card.getId(), fixture.user);
        var second = ttsService.generateForCard(fixture.card.getId(), fixture.user);

        assertFalse(first.isCached());
        assertTrue(second.isCached());
        assertEquals(first.getId(), second.getId());
        assertEquals(1, SYNTHESIS_CALLS.get());
    }

    @Test
    void audioStreamingRequiresDeckOwnership() throws Exception {
        Fixture fixture = createTtsFixture();
        var audio = ttsService.generateForCard(fixture.card.getId(), fixture.user);
        User otherUser = UserDAOTests.createUser(userDAO, themeDAO);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> ttsService.getOwnedAudio(audio.getId(), otherUser)
        );
        assertEquals(404, ex.getStatusCode().value());
    }

    private Fixture createTtsFixture() {
        Deck deck = DeckDAOTests.createDeck(deckDAO, userDAO, tagDAO, themeDAO);
        deck.setTtsEnabled(true);
        deck.setTtsModelId("test/model");
        deckDAO.save(deck);

        TtsPreset preset = new TtsPreset();
        preset.setDeck(deck);
        preset.setName("Clear voice");
        preset.setSpeaker("Leela");
        preset.setLanguage("English");
        preset.setCaption("Leela speaks clearly with a moderate pace.");
        preset.setAdvancedConfigJson("{\"do_sample\":false}");
        ttsPresetDAO.save(preset);
        deck.setTtsDefaultPresetId(preset.getId());
        deckDAO.save(deck);

        Card card = new Card();
        card.setDeck(deck);
        card.setFront("Front");
        card.setBack("Back");
        card.setTtsText("Hello from the test card.");
        card.setTtsDisplaySide("BACK");
        card.setTags(Set.of(TagDAOTests.createTag(tagDAO)));
        cardDAO.save(card);

        return new Fixture(deck, card, preset, deck.getUser());
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/synthesize", exchange -> {
                SYNTHESIS_CALLS.incrementAndGet();
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (!requestBody.contains("test/model") || !requestBody.contains("Hello from the test card.")) {
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

    private record Fixture(Deck deck, Card card, TtsPreset preset, User user) {
    }
}
