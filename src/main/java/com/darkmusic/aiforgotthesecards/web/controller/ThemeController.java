package com.darkmusic.aiforgotthesecards.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.darkmusic.aiforgotthesecards.business.entities.Theme;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.ThemeDAO;
import lombok.Getter;

@Getter
@RestController
public class ThemeController {
  private final ThemeDAO themeDAO;

  public ThemeController(ThemeDAO themeDAO) {
    this.themeDAO = themeDAO;
  }

  @GetMapping("/api/theme/{id}")
  public Theme getTheme(@PathVariable long id) {
    return themeDAO.findById(id).orElse(null);
  }

  @GetMapping("/api/themes")
  public Iterable<Theme> getAllThemes() {
    return themeDAO.findAll();
  }
}
