package com.darkmusic.aiforgotthesecards.web.contracts;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TtsPlaybackItem {
    private String target;
    private String variant;
    private String label;
    private String language;
    private String textSource;
    private String text;
    private String displaySide;
    private String modelId;
    private Long audioId;
    private String audioUrl;
    private boolean cached;
}
