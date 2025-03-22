package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.Theme;
import org.springframework.data.repository.CrudRepository;

public interface ThemeDAO extends CrudRepository<Theme, Long> {
}
