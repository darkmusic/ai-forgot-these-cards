package com.darkmusic.aiforgotthesecards.runners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;

import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import org.springframework.stereotype.Component;
import org.mindrot.jbcrypt.BCrypt;

@Component
@Order(2)
public class InitUserRunner implements CommandLineRunner {
    private final UserDAO userDAO;
    private final String defaultUsername = "cards";
    private final String defaultPlaintextPassword = "cards";
    private static final Logger logger = LoggerFactory.getLogger(InitUserRunner.class);

    public InitUserRunner(UserDAO userDAO) {
        this.userDAO = userDAO;
    }


    @Override
    public void run(String... args) throws Exception {
        if (userDAO.findByUsername(defaultUsername).isEmpty()) {
            User user = new User();
            user.setName(defaultUsername);
            user.setUsername(defaultUsername);
            user.setPassword_hash(BCrypt.hashpw(defaultPlaintextPassword, BCrypt.gensalt()));
            user.setProfile_pic_url("/vite.svg");
            user.setActive(true);
            user.setAdmin(true);
            user.setThemeId(1L); // Assuming the default theme ID is 1
            userDAO.save(user);
            logger.info("Default user created: {} with password: {}", defaultUsername, defaultPlaintextPassword);
        } else {
            logger.info("Default user already exists: {}", defaultUsername);
        }
    }
}
