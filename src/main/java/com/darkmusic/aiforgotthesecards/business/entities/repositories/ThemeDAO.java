package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.Theme;

import jakarta.transaction.Transactional;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

@Transactional
public interface ThemeDAO extends CrudRepository<Theme, Long> {
      Optional<Theme> findByName(String name);
}
