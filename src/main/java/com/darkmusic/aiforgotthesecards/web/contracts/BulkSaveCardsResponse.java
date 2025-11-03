package com.darkmusic.aiforgotthesecards.web.contracts;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BulkSaveCardsResponse {
    private int created;
    private int updated;
    private int deleted;
}
