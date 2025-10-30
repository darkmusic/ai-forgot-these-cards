package com.darkmusic.aiforgotthesecards.web.controller;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.UserCardSrs;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.CardDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.DeckDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserCardSrsDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import com.darkmusic.aiforgotthesecards.business.entities.services.SrsService;
import com.darkmusic.aiforgotthesecards.web.contracts.SrsCardResponse;
import com.darkmusic.aiforgotthesecards.web.contracts.SrsReviewRequest;
import jakarta.persistence.EntityManager;
import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@RestController
public class SrsController {
    private final SrsService srsService;
    private final UserDAO userDAO;
    private final CardDAO cardDAO;
    private final DeckDAO deckDAO;
    private final UserCardSrsDAO userCardSrsDAO;
    private final EntityManager em;

    public SrsController(SrsService srsService, UserDAO userDAO, CardDAO cardDAO,
                        DeckDAO deckDAO, UserCardSrsDAO userCardSrsDAO, EntityManager em) {
        this.srsService = srsService;
        this.userDAO = userDAO;
        this.cardDAO = cardDAO;
        this.deckDAO = deckDAO;
        this.userCardSrsDAO = userCardSrsDAO;
        this.em = em;
    }

    /**
     * Get the review queue for the authenticated user.
     * Returns cards that are due for review (nextReviewAt <= now) or cards that have never been reviewed.
     *
     * @param authentication Spring Security authentication object
     * @param deckId Optional deck ID to filter by specific deck
     * @return List of cards due for review with SRS metadata
     */
    @GetMapping("/api/srs/review-queue")
    public List<SrsCardResponse> getReviewQueue(
            Authentication authentication,
            @RequestParam(required = false) Long deckId) {

        if (authentication == null || authentication.getName() == null) {
            return new ArrayList<>();
        }

        User user = userDAO.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return new ArrayList<>();
        }

        List<SrsCardResponse> reviewQueue = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // Get all cards for the user (optionally filtered by deck)
        Iterable<Card> allCards;
        if (deckId != null) {
            Deck deck = deckDAO.findById(deckId).orElse(null);
            if (deck == null || !deck.getUser().getId().equals(user.getId())) {
                return new ArrayList<>();
            }
            allCards = cardDAO.findByDeck(deck);
        } else {
            allCards = cardDAO.findByDeckUser(user);
        }

        // Check each card's SRS status
        for (Card card : allCards) {
            Optional<UserCardSrs> srsRecord = userCardSrsDAO.findByUserAndCard(user, card);

            SrsCardResponse response = new SrsCardResponse();
            response.setCard(card);

            if (srsRecord.isEmpty()) {
                // Never reviewed - add to queue
                response.setNew(true);
                response.setRepetitions(0);
                reviewQueue.add(response);
            } else {
                UserCardSrs srs = srsRecord.get();
                response.setNew(false);
                response.setNextReviewAt(srs.getNextReviewAt());
                response.setIntervalDays(srs.getIntervalDays());
                response.setRepetitions(srs.getRepetitions());

                // Add if due for review
                if (srs.getNextReviewAt().isBefore(now) || srs.getNextReviewAt().isEqual(now)) {
                    reviewQueue.add(response);
                }
            }
        }

        return reviewQueue;
    }

    /**
     * Process a card review.
     *
     * @param authentication Spring Security authentication object
     * @param request Review request containing cardId and quality rating (0-5)
     * @return Updated SRS record
     */
    @PostMapping("/api/srs/review")
    public UserCardSrs processReview(
            Authentication authentication,
            @RequestBody SrsReviewRequest request) {

        if (authentication == null || authentication.getName() == null) {
            return null;
        }

        User user = userDAO.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return null;
        }

        Card card = cardDAO.findById(request.getCardId()).orElse(null);
        if (card == null) {
            return null;
        }

        // Verify the card belongs to a deck owned by this user
        if (!card.getDeck().getUser().getId().equals(user.getId())) {
            return null;
        }

        // Validate quality is in range 0-5
        int quality = Math.max(0, Math.min(5, request.getQuality()));

        return srsService.processReview(user, card, quality);
    }

    /**
     * Get SRS statistics for the authenticated user.
     *
     * @param authentication Spring Security authentication object
     * @return Statistics about the user's SRS progress
     */
    @GetMapping("/api/srs/stats")
    public SrsStatsResponse getStats(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return new SrsStatsResponse();
        }

        User user = userDAO.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return new SrsStatsResponse();
        }

        LocalDateTime now = LocalDateTime.now();
        List<UserCardSrs> allSrsRecords = userCardSrsDAO.findByUser(user);

        long totalCards = ((List<Card>) cardDAO.findByDeckUser(user)).size();
        long reviewedCards = allSrsRecords.size();
        long newCards = totalCards - reviewedCards;
        long dueCards = allSrsRecords.stream()
                .filter(srs -> srs.getNextReviewAt().isBefore(now) || srs.getNextReviewAt().isEqual(now))
                .count();

        SrsStatsResponse stats = new SrsStatsResponse();
        stats.setTotalCards(totalCards);
        stats.setReviewedCards(reviewedCards);
        stats.setNewCards(newCards);
        stats.setDueCards(dueCards + newCards); // All new cards are also "due"

        return stats;
    }

    // Inner class for stats response
    @Getter
    @lombok.Setter
    public static class SrsStatsResponse {
        private long totalCards;
        private long reviewedCards;
        private long newCards;
        private long dueCards;
    }
}
