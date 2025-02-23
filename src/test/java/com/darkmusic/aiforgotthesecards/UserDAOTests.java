package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource("/application-test.properties")
public class UserDAOTests {
    @Autowired
    private UserDAO userDAO;

    static User createUser(UserDAO userDAO) {
        var user = new User();
        user.setName("Test User");
        user.setUsername("testuser" + System.currentTimeMillis());
        user.setPassword_hash("password");
        userDAO.save(user);
        return user;
    }

    @Test
    void canCreateUser() {
        System.out.println("Testing user creation");
        createUser(userDAO);
    }
}
