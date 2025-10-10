package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.*;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.*;
import com.darkmusic.aiforgotthesecards.web.controller.AiController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import java.util.Set;

@SpringBootTest
@TestPropertySource("/application-test.properties")
class AiForgotTheseCardsApplicationTests {
    @Autowired
    private AiController aiController;

    @Test
    void contextLoads() {
    }
}
