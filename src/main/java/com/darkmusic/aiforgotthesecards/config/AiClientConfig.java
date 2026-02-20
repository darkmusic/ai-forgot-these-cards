package com.darkmusic.aiforgotthesecards.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Configures the HTTP client used by Spring AI (OpenAI-compatible) to call the
 * AI backend (e.g. llama.cpp). Without an explicit read timeout the RestClient
 * blocks the Tomcat thread indefinitely, and the first thing that terminates the
 * request is the network layer (e.g. the Nginx proxy_read_timeout), which sends
 * a TCP reset that the browser sees as a TypeError rather than a clean HTTP error.
 *
 * <p>Timeout cascade (each layer must be longer than the one before it):
 * <ol>
 *   <li>Spring AI HTTP client read timeout: {@code ai.client.read-timeout} (default 10 min)</li>
 *   <li>Nginx {@code proxy_read_timeout}: must exceed the above so Spring sends a
 *       clean 500 before Nginx cuts the connection (set to 11 min in nginx.conf)</li>
 *   <li>Browser AbortController: must exceed the Nginx timeout so the client never
 *       aborts before the server responds (set to 12 min in EditCard.tsx)</li>
 * </ol>
 */
@Configuration
public class AiClientConfig {

    /**
     * Maximum time to wait for the AI backend to produce a response.
     * Configured via {@code AI_CLIENT_READ_TIMEOUT} environment variable
     * (e.g. {@code 10m}, {@code PT10M}, {@code 600s}).
     * Falls back to the property value in {@code application.properties},
     * which itself defaults to {@code PT10M} (10 minutes).
     */
    @Value("${ai.client.read-timeout:PT10M}")
    private Duration readTimeout;

    /**
     * Applies the read timeout to every {@code RestClient} instance created by
     * Spring Boot's auto-configuration, including the one used by Spring AI's
     * OpenAI integration. This ensures llama.cpp inference that exceeds the
     * timeout results in a clean HTTP 500 response sent back to the browser
     * rather than a silent TCP reset.
     */
    @Bean
    public RestClientCustomizer aiClientReadTimeoutCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setReadTimeout(readTimeout);
            builder.requestFactory(factory);
        };
    }
}
