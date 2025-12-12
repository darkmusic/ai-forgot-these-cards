package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.ThemeDAO;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource("/application-test.properties")
public class UserDAOTests {
    @Autowired
    private UserDAO userDAO;

    @Autowired
    private ThemeDAO themeDAO;

    static User createUser(UserDAO userDAO, ThemeDAO themeDAO) {
        var user = new User();
        user.setName("Test User");
        user.setUsername("testuser" + System.currentTimeMillis());
        user.setPassword_hash("password");
        user.setProfile_pic_url("profile_pic_url");
        user.setThemeId(ThemeDAOTests.createTheme(themeDAO).getId());
        userDAO.save(user);
        return user;
    }

    @Test
    void canCreateUser() {
        System.out.println("Testing user creation");
        createUser(userDAO, themeDAO);
    }

    @Test
    void findByUsernameIsCaseInsensitive() {
        var user = new User();
        user.setName("Case User");
        user.setUsername("MiXeDCaSe" + System.currentTimeMillis());
        user.setPassword_hash("password");
        user.setProfile_pic_url("profile_pic_url");
        user.setThemeId(ThemeDAOTests.createTheme(themeDAO).getId());
        userDAO.save(user);

        assertTrue(userDAO.findByUsername(user.getUsername().toLowerCase()).isPresent());
        assertTrue(userDAO.findByUsername(user.getUsername().toUpperCase()).isPresent());
    }
}
