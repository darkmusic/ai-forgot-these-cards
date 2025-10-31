package com.darkmusic.aiforgotthesecards.web.contracts;

import lombok.Getter;
import lombok.Setter;

/**
 * Simplified deck information for SRS responses.
 * Avoids circular reference issues with full Deck entity.
 */
@Setter
@Getter
public class DeckInfo {
    private Long id;
    private String name;
    private String templateFront;
    private String templateBack;
}
