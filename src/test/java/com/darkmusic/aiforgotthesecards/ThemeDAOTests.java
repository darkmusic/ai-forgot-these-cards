package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.Theme;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource("/application-test.properties")
public class ThemeDAOTests {
    @Autowired
    private ThemeDAO themeDAO;

    static Theme createTheme(ThemeDAO ThemeDAO) {
        var Theme = new Theme();
        Theme.setName("Test Theme " + System.currentTimeMillis());
        ThemeDAO.save(Theme);
        return Theme;
    }

    @Test
    void canCreateTheme() {
        System.out.println("Testing theme creation");
        createTheme(themeDAO);
    }
}
