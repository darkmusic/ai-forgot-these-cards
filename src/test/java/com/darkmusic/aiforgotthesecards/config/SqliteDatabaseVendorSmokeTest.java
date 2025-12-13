package com.darkmusic.aiforgotthesecards.config;

import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "DB_VENDOR=sqlite",
        "SQLITE_DB_PATH=target/test-sqlite/cards-test.db",
        // keep the test isolated
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
    }
)
class SqliteDatabaseVendorSmokeTest {

    private static final Path DB_FILE = Path.of("target", "test-sqlite", "cards-test.db");

    static {
        try {
            Files.createDirectories(DB_FILE.toAbsolutePath().getParent());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create temp SQLite directory for test", e);
        }
    }

    @Autowired
    private UserDAO userDAO;

    @Test
    void bootsAndCanPersistUser() {
        User user = new User();
        user.setUsername("sqlite_smoke_user");
        user.setName("SQLite Smoke");
        user.setPassword_hash("$2b$10$cdHhlMdofgY0HJ1EYYXuK.6WqOXHcv9nzhHSCHnMkKXh1pwt0yWd6");
        user.setProfile_pic_url("/vite.svg");
        user.setActive(true);
        user.setAdmin(false);

        User saved = userDAO.save(user);
        assertThat(saved.getId()).isNotNull();

        var loaded = userDAO.findById(saved.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getUsername()).isEqualTo("sqlite_smoke_user");
    }
}
