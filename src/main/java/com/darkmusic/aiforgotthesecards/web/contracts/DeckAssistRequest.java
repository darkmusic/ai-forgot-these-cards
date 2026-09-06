package com.darkmusic.aiforgotthesecards.web.contracts;

import java.util.List;

/** A snapshot of the editor, not database entities. Row IDs also identify unsaved cards. */
public record DeckAssistRequest(String operation, String deckName, List<DraftCard> cards,
                               Integer count, String difficulty, List<String> topics,
                               String instructions) {
    public record DraftCard(String rowId, String front, String back, List<String> tags) {}
}
