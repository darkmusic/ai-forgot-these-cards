package com.darkmusic.aiforgotthesecards.web.contracts;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class DeckTtsSettings {
    private boolean ttsEnabled;
    private String ttsModelId;
    private Long ttsDefaultPresetId;
    private List<TtsPresetInfo> presets = new ArrayList<>();
}
