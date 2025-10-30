package com.darkmusic.aiforgotthesecards.web.contracts;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class SrsCardResponse {
    private Card card;
    private LocalDateTime nextReviewAt;
    private Integer intervalDays;
    private Integer repetitions;
    private boolean isNew; // True if card has never been reviewed
}
