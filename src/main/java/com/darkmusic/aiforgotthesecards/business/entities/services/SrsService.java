package com.darkmusic.aiforgotthesecards.business.entities.services;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.UserCardSrs;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserCardSrsDAO;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SrsService {
    private final UserCardSrsDAO userCardSrsDAO;

    // Default ease factor for new cards
    private static final float DEFAULT_EASE_FACTOR = 2.5f;
    private static final float MIN_EASE_FACTOR = 1.3f;

    public SrsService(UserCardSrsDAO userCardSrsDAO) {
        this.userCardSrsDAO = userCardSrsDAO;
    }

    /**
     * Process a review for a card.
     * Quality scale: 0-5
     *   0-2: Incorrect/forgot
     *   3: Recalled with difficulty
     *   4: Recalled after brief hesitation
     *   5: Perfect recall
     *
     * @param user The user reviewing the card
     * @param card The card being reviewed
     * @param quality The quality of recall (0-5)
     * @return The updated UserCardSrs record
     */
    @Transactional
    public UserCardSrs processReview(User user, Card card, int quality) {
        Optional<UserCardSrs> existing = userCardSrsDAO.findByUserAndCard(user, card);

        UserCardSrs srs;
        if (existing.isPresent()) {
            srs = existing.get();
        } else {
            // First time reviewing this card
            srs = new UserCardSrs();
            srs.setUser(user);
            srs.setCard(card);
            srs.setRepetitions(0);
            srs.setEaseFactor(DEFAULT_EASE_FACTOR);
            srs.setIntervalDays(0);
        }

        // Update last reviewed timestamp
        srs.setLastReviewedAt(LocalDateTime.now());

        // Apply SM-2 algorithm
        if (quality < 3) {
            // Incorrect response - reset
            srs.setRepetitions(0);
            srs.setIntervalDays(1);
        } else {
            // Correct response
            int repetitions = srs.getRepetitions() + 1;
            srs.setRepetitions(repetitions);

            // Calculate new interval
            int newInterval;
            if (repetitions == 1) {
                newInterval = 1;
            } else if (repetitions == 2) {
                newInterval = 6;
            } else {
                newInterval = Math.round(srs.getIntervalDays() * srs.getEaseFactor());
            }
            srs.setIntervalDays(newInterval);

            // Update ease factor
            float newEaseFactor = srs.getEaseFactor() + (0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f));
            if (newEaseFactor < MIN_EASE_FACTOR) {
                newEaseFactor = MIN_EASE_FACTOR;
            }
            srs.setEaseFactor(newEaseFactor);
        }

        // Set next review date
        srs.setNextReviewAt(LocalDateTime.now().plusDays(srs.getIntervalDays()));

        return userCardSrsDAO.save(srs);
    }

    /**
     * Get the SRS record for a user and card, if it exists.
     */
    public Optional<UserCardSrs> getSrsRecord(User user, Card card) {
        return userCardSrsDAO.findByUserAndCard(user, card);
    }
}
