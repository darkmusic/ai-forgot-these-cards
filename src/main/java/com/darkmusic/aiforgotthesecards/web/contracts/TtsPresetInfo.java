package com.darkmusic.aiforgotthesecards.web.contracts;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TtsPresetInfo {
    private Long id;
    private String name;
    private String speaker;
    private String language;
    private String caption;
    private String advancedConfigJson;
    private int sortOrder;
}
