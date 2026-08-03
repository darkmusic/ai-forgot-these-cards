package com.darkmusic.aiforgotthesecards.web.contracts;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DeckTtsSettings {
    private boolean ttsEnabled;
    private String ttsModelId;
    private String ttsConfigJson;
}
