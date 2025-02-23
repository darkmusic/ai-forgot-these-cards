package com.darkmusic.aiforgotthesecards;

import com.darkmusic.aiforgotthesecards.business.entities.Tag;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.TagDAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource("/application-test.properties")
public class TagDAOTests {
    @Autowired
    private TagDAO tagDAO;

    static Tag createTag(TagDAO tagDAO) {
        var tag = new Tag();
        tag.setName("Test Tag " + System.currentTimeMillis());
        tagDAO.save(tag);
        return tag;
    }

    @Test
    void canCreateTag() {
        System.out.println("Testing tag creation");
        createTag(tagDAO);
    }
}
