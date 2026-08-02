package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.Deck;
import com.darkmusic.aiforgotthesecards.business.entities.TtsPreset;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface TtsPresetDAO extends CrudRepository<TtsPreset, Long> {
    List<TtsPreset> findByDeckOrderBySortOrderAscIdAsc(Deck deck);

    void deleteByDeck(Deck deck);
}
