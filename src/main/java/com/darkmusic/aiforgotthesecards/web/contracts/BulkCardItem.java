package com.darkmusic.aiforgotthesecards.web.contracts;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkCardItem {
    private Long id; // null for create
    private Long deckId; // required
    private String front;
    private String back;
    private List<String> tags; // tag names
}
