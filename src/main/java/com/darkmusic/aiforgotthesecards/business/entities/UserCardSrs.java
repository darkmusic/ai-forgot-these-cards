package com.darkmusic.aiforgotthesecards.business.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "user_card_srs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "card_id"})
})
public class UserCardSrs {
    @Id
    @GeneratedValue
    @Column(name="id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(name="next_review_at", nullable = false)
    private LocalDateTime nextReviewAt;

    @Column(name="interval_days", nullable = false)
    private Integer intervalDays;

    @Column(name="ease_factor", nullable = false)
    private Float easeFactor;

    @Column(name="repetitions", nullable = false)
    private Integer repetitions;

    @Column(name="last_reviewed_at")
    private LocalDateTime lastReviewedAt;
}
