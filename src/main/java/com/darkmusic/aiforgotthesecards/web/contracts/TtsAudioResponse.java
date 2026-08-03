package com.darkmusic.aiforgotthesecards.web.contracts;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TtsAudioResponse {
    private Long id;
    private Long cardId;
    private Long deckId;
    private String cacheKey;
    private String contentType;
    private long generatedAt;
    private String modelId;
    private String target;
    private String variant;
    private String language;
    private String textSource;
    private String resolvedText;
    private String voice;
    private Double speed;
    private String audioUrl;
    private boolean cached;
}
