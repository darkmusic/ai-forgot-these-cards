package com.darkmusic.aiforgotthesecards.web.contracts;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkSaveCardsRequest {
    private List<BulkCardItem> create;
    private List<BulkCardItem> update;
    private List<Long> deleteIds;
}
