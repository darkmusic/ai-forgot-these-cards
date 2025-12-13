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

    // Spring AI OpenAI URLs
    private static final String OPENAI_BASE_URL = "spring.ai.openai.base-url";
    private static final String OPENAI_CHAT_BASE_URL = "spring.ai.openai.chat.base-url";

    // Any non-empty value satisfies Spring AI's startup validation.
    private static final String FALLBACK_API_KEY_VALUE = "not-set";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String apiKey = environment.getProperty(OPENAI_API_KEY);
        String chatApiKey = environment.getProperty(OPENAI_CHAT_API_KEY);

        String baseUrl = environment.getProperty(OPENAI_BASE_URL);
        String chatBaseUrl = environment.getProperty(OPENAI_CHAT_BASE_URL);

        // If either property is explicitly present-but-empty, Spring AI fails startup.
        // Treat empty as "unset" by supplying a harmless non-empty placeholder.
        boolean needsApiKey = apiKey != null && !hasText(apiKey);
        boolean needsChatApiKey = chatApiKey != null && !hasText(chatApiKey);

        Map<String, Object> overrides = new HashMap<>();

        // Normalize common misconfiguration:
        // Spring AI's OpenAiApi builds the /v1/... paths internally, so setting base-url to .../v1
        // results in .../v1/v1/... and a confusing 404 from OpenAI-compatible servers.
        String normalizedBaseUrl = normalizeOpenAiBaseUrl(baseUrl);
        if (normalizedBaseUrl != null) {
            overrides.put(OPENAI_BASE_URL, normalizedBaseUrl);
        }
        String normalizedChatBaseUrl = normalizeOpenAiBaseUrl(chatBaseUrl);
        if (normalizedChatBaseUrl != null) {
            overrides.put(OPENAI_CHAT_BASE_URL, normalizedChatBaseUrl);
        }

        if (needsApiKey) {
            overrides.put(OPENAI_API_KEY, FALLBACK_API_KEY_VALUE);
        }
        if (needsChatApiKey) {
            overrides.put(OPENAI_CHAT_API_KEY, FALLBACK_API_KEY_VALUE);
        }

        if (overrides.isEmpty()) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
    }

    private static String normalizeOpenAiBaseUrl(String value) {
        if (!hasText(value)) {
            return null;
        }

        // Trim trailing slash first for consistent comparison.
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        if (trimmed.endsWith("/v1")) {
            return trimmed.substring(0, trimmed.length() - 3);
        }

        return null;
    }

    @Override
    public int getOrder() {
        // Run early so downstream auto-config sees the corrected values.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
