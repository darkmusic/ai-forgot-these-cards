package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.AiModel;
import jakarta.persistence.TypedQuery;
import org.springframework.data.repository.CrudRepository;
import java.util.List;

public interface AiModelDAO extends CrudRepository<AiModel, Long> {
    List<AiModel> query(TypedQuery<AiModel> query);
}
