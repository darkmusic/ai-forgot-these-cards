package com.darkmusic.aiforgotthesecards.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiApiKeyDefaultingEnvironmentPostProcessorTest {

    @Test
    void defaultsEmptyOpenAiApiKeyToNonEmptyPlaceholder() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.ai.openai.api-key", "");

        new OpenAiApiKeyDefaultingEnvironmentPostProcessor()
                .postProcessEnvironment(env, new SpringApplication());

        assertEquals("not-set", env.getProperty("spring.ai.openai.api-key"));
    }

    @Test
    void defaultsEmptyOpenAiChatApiKeyToNonEmptyPlaceholder() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.ai.openai.chat.api-key", "");

        new OpenAiApiKeyDefaultingEnvironmentPostProcessor()
                .postProcessEnvironment(env, new SpringApplication());

        assertEquals("not-set", env.getProperty("spring.ai.openai.chat.api-key"));
    }

    @Test
    void doesNotOverrideRealKey() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.ai.openai.api-key", "sk-real");

        new OpenAiApiKeyDefaultingEnvironmentPostProcessor()
                .postProcessEnvironment(env, new SpringApplication());

        assertEquals("sk-real", env.getProperty("spring.ai.openai.api-key"));
    }
}
