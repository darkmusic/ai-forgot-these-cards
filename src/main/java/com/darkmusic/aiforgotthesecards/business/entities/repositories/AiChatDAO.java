package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.AiChat;
import jakarta.persistence.TypedQuery;
import org.springframework.data.repository.CrudRepository;
import java.util.List;

public interface AiChatDAO extends CrudRepository<AiChat, Long> {
    List<AiChat> query(TypedQuery<AiChat> query);
}
