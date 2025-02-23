package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.AiModel;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.AiModelDAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource("/application-test.properties")
public class AiModelDAOTests {
    @Autowired
    private AiModelDAO aiModelDAO;

    static AiModel createAiModel(AiModelDAO aiModelDAO) {
        var aiModel = new AiModel();
        aiModel.setName("Test AiModel " + System.currentTimeMillis());
        aiModel.setModel("Test AiModel Model");
        aiModelDAO.save(aiModel);
        return aiModel;
    }

    @Test
    void canCreateAiModel() {
        System.out.println("Testing aiModel creation");
        createAiModel(aiModelDAO);
    }
}
