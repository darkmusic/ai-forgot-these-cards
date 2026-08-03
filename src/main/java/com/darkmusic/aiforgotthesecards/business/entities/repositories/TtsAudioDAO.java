package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.TtsAudio;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface TtsAudioDAO extends CrudRepository<TtsAudio, Long> {
    Optional<TtsAudio> findByCardAndCacheKey(Card card, String cacheKey);
}
