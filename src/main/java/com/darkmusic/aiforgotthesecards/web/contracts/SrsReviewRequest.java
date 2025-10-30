package com.darkmusic.aiforgotthesecards.web.contracts;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SrsReviewRequest {
    private Long cardId;
    private int quality; // 0-5 quality rating
}
