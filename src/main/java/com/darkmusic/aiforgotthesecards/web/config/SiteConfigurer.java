package com.darkmusic.aiforgotthesecards.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.darkmusic.aiforgotthesecards.web.controller.AiController;
import com.darkmusic.aiforgotthesecards.web.controller.UserController;

@Configuration
public class SiteConfigurer implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // registry.addViewController("/").setViewName("index.html");
        // registry.addViewController("/home").setViewName("index.html");
        registry.addViewController("/api/ai").setViewName(AiController.class.getName());
        registry.addViewController("/api/user").setViewName(UserController.class.getName());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:8086", "http://localhost:8080", "http://web:8086", "http://app:8080")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*");
    }
}
