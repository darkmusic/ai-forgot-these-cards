package com.darkmusic.aiforgotthesecards.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.util.StringUtils.hasText;

public class OpenAiApiKeyDefaultingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "aiForgotTheseCardsOpenAiDefaults";

    private static final String OPENAI_API_KEY = "spring.ai.openai.api-key";
    private static final String OPENAI_CHAT_API_KEY = "spring.ai.openai.chat.api-key";

    // Any non-empty value satisfies Spring AI's startup validation.
    private static final String FALLBACK_API_KEY_VALUE = "not-set";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String apiKey = environment.getProperty(OPENAI_API_KEY);
        String chatApiKey = environment.getProperty(OPENAI_CHAT_API_KEY);

        // If either property is explicitly present-but-empty, Spring AI fails startup.
        // Treat empty as "unset" by supplying a harmless non-empty placeholder.
        boolean needsApiKey = apiKey != null && !hasText(apiKey);
        boolean needsChatApiKey = chatApiKey != null && !hasText(chatApiKey);

        if (!needsApiKey && !needsChatApiKey) {
            return;
        }

        Map<String, Object> overrides = new HashMap<>();
        if (needsApiKey) {
            overrides.put(OPENAI_API_KEY, FALLBACK_API_KEY_VALUE);
        }
        if (needsChatApiKey) {
            overrides.put(OPENAI_CHAT_API_KEY, FALLBACK_API_KEY_VALUE);
        }

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
    }

    @Override
    public int getOrder() {
        // Run early so downstream auto-config sees the corrected values.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
