package com.darkmusic.aiforgotthesecards.web.contracts;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TtsAudioResponse {
    private Long id;
    private Long cardId;
    private Long deckId;
    private Long presetId;
    private String cacheKey;
    private String contentType;
    private long generatedAt;
    private String modelId;
    private String presetName;
    private String audioUrl;
    private boolean cached;
}
