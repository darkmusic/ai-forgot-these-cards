package com.darkmusic.aiforgotthesecards.runners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.darkmusic.aiforgotthesecards.business.entities.Theme;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.ThemeDAO;

@Component
@Order(1)
public class InitThemeRunner implements CommandLineRunner {
    private final ThemeDAO themeDAO;
    private static final Logger logger = LoggerFactory.getLogger(InitThemeRunner.class);

    public InitThemeRunner(ThemeDAO themeDAO) {
        this.themeDAO = themeDAO;
    }

    @Override
    public void run(String... args) throws Exception {
        if (themeDAO.findByName("Dark").isEmpty()) {
            Theme theme = new Theme();
            theme.setName("Dark");
            theme.setDescription("Dark theme");
            theme.setCssUrl("/css/Dark.css");
            theme.setActive(true);
            themeDAO.save(theme);
            logger.info("Dark theme created.");
        } else {
            logger.info("Dark theme already exists.");
        }

        if (themeDAO.findByName("Light").isEmpty()) {
            Theme theme = new Theme();
            theme.setName("Light");
            theme.setDescription("Light theme");
            theme.setCssUrl("/css/Light.css");
            theme.setActive(true);
            themeDAO.save(theme);
            logger.info("Light theme created.");
        } else {
            logger.info("Light theme already exists.");
        }
    }
}
