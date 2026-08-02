package com.darkmusic.aiforgotthesecards.web.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TtsSynthesisRequest {
    private String modelId;
    private String text;
    private String caption;
    private String speaker;
    private String language;
    private JsonNode generationConfig;
}
