package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.AiChat;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource("/application-test.properties")
public class AiChatDAOTests {
    @Autowired
    private AiChatDAO aiChatDAO;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private ThemeDAO themeDAO;

    static AiChat createAiChat(AiChatDAO aiChatDAO, UserDAO userDAO, ThemeDAO themeDAO) {
        var user = UserDAOTests.createUser(userDAO, themeDAO);
        var aiChat = new AiChat();
        aiChat.setQuestion("Test Question " + System.currentTimeMillis());
        aiChat.setAnswer("Test Answer");
        aiChat.setUser(user);
        aiChat.setCreatedAt(System.currentTimeMillis());
        aiChatDAO.save(aiChat);
        return aiChat;
    }

    @Test
    void canCreateAiChat() {
        System.out.println("Testing ai chat creation");
        createAiChat(aiChatDAO, userDAO, themeDAO);
    }
}
