package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.*;
import com.darkmusic.aiforgotthesecards.web.contracts.BulkCardItem;
import com.darkmusic.aiforgotthesecards.web.contracts.BulkSaveCardsRequest;
import com.darkmusic.aiforgotthesecards.web.controller.CardController;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@TestPropertySource("/application-test.properties")
@AutoConfigureMockMvc
class BulkAiSaveTests {
    @Autowired CardController controller;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;

    record Fixture(Long survivor, Long removed, Long survivorReview, Long removedReview, Long audio, Long deck) {}

    // This test class runs with its own Spring context (docker-compose is disabled),
    // so give it a dedicated in-memory DB. Otherwise its tag/deck mutations would leak
    // into the shared "testdb" database used by the other @SpringBootTest classes.
    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:bulksavetests;DB_CLOSE_DELAY=-1");
    }

    private Fixture fixture() {
        return new TransactionTemplate(transactions).execute(status -> {
            User user = new User(); user.setUsername("merge-" + java.util.UUID.randomUUID()); user.setName("Merge test"); user.setPassword_hash("unused"); em.persist(user);
            Deck deck = new Deck(); deck.setName("Merge test"); deck.setUser(user); em.persist(deck);
            Tag tag = new Tag(); tag.setName("Original-" + java.util.UUID.randomUUID()); em.persist(tag);
            Card survivor = new Card(); survivor.setDeck(deck); survivor.setFront("Original question"); survivor.setBack("Original answer");
            survivor.setTags(java.util.Set.of(tag)); em.persist(survivor);
            survivor.setTtsConfigJson("{\"voice\":\"keep\"}");
            Card removed = new Card(); removed.setDeck(survivor.getDeck()); removed.setFront("Duplicate"); removed.setBack("Answer");
            em.persist(removed);
            var keepReview = review(survivor);
            var dropReview = review(removed);
            TtsAudio audio = new TtsAudio(); audio.setCard(removed); audio.setDeck(removed.getDeck());
            audio.setCacheKey("test"); audio.setFilePath("test-unused.wav"); audio.setModelId("test");
            em.persist(audio); em.flush();
            return new Fixture(survivor.getId(), removed.getId(), keepReview.getId(), dropReview.getId(), audio.getId(), survivor.getDeck().getId());
        });
    }

    private UserCardSrs review(Card card) {
        var review = new UserCardSrs(); review.setCard(card); review.setUser(card.getDeck().getUser());
        review.setNextReviewAt(LocalDateTime.now().plusDays(3)); review.setIntervalDays(3); review.setEaseFactor(2.5f); review.setRepetitions(4);
        em.persist(review); return review;
    }

    private BulkSaveCardsRequest mergeRequest(Fixture fixture) {
        var item = new BulkCardItem(); item.setId(fixture.survivor()); item.setDeckId(fixture.deck());
        item.setFront("Merged question"); item.setBack("Enhanced answer"); item.setTags(List.of("Geography", "Science"));
        var request = new BulkSaveCardsRequest(); request.setUpdate(List.of(item)); request.setDeleteIds(List.of(fixture.removed()));
        return request;
    }

    @Test void savesMergedReviewedCardsAndPreservesSurvivorMetadata() {
        Fixture fixture = fixture();
        var result = controller.bulkSave(mergeRequest(fixture));
        assertEquals(1, result.getDeleted()); assertEquals(1, result.getUpdated());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Card saved = em.find(Card.class, fixture.survivor());
            assertEquals("Merged question", saved.getFront());
            assertEquals("{\"voice\":\"keep\"}", saved.getTtsConfigJson());
            assertEquals(2, saved.getTags().size());
            assertEquals(4, em.find(UserCardSrs.class, fixture.survivorReview()).getRepetitions());
            assertNull(em.find(Card.class, fixture.removed()));
            assertNull(em.find(UserCardSrs.class, fixture.removedReview()));
            assertNull(em.find(TtsAudio.class, fixture.audio()));
        });
    }

    @Test void aFailedBulkSaveRollsBackMergeDeletionsAndUpdates() {
        Fixture fixture = fixture();
        var request = mergeRequest(fixture);
        var invalid = new BulkCardItem(); invalid.setDeckId(-999L); invalid.setFront("Invalid deck"); invalid.setBack("Answer");
        request.setCreate(List.of(invalid));
        assertThrows(RuntimeException.class, () -> controller.bulkSave(request));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertNotEquals("Merged question", em.find(Card.class, fixture.survivor()).getFront());
            assertNotNull(em.find(Card.class, fixture.removed()));
            assertNotNull(em.find(UserCardSrs.class, fixture.removedReview()));
            assertNotNull(em.find(TtsAudio.class, fixture.audio()));
        });
    }

    @Test void deckAssistRequiresAuthenticationAndCsrf() throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ai/deck-assist")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}");
        mvc.perform(request.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/ai/deck-assist")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test"))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
}
