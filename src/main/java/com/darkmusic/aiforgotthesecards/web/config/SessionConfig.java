package com.darkmusic.aiforgotthesecards.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for session management, including session timeout settings.
 * This is necessary because Tomcat's session timeout setting overrides the Spring Boot property.
 */
@Configuration
public class SessionConfig {

  @Value("${SESSION_TIMEOUT:30m}")
  private String sessionTimeout;

  @Bean
  public ServletContextInitializer servletContextInitializer() {
    return servletContext -> {
      // Parse timeout (e.g., "1m" -> 1 minute)
      int minutes = parseTimeoutToMinutes(sessionTimeout);
      servletContext.setSessionTimeout(minutes);
    };
  }

  private int parseTimeoutToMinutes(String timeout) {
    if (timeout.endsWith("m")) {
      return Integer.parseInt(timeout.substring(0, timeout.length() - 1));
    } else if (timeout.endsWith("h")) {
      int hours = Integer.parseInt(timeout.substring(0, timeout.length() - 1));
      return hours * 60; // Convert hours to minutes
    } else if (timeout.endsWith("s")) {
      int seconds = Integer.parseInt(timeout.substring(0, timeout.length() - 1));
      return Math.max(1, seconds / 60); // Servlet API uses minutes
    }
    return 30; // Default fallback
  }
}
