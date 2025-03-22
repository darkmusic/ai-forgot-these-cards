package com.darkmusic.aiforgotthesecards.web.config;

import com.darkmusic.aiforgotthesecards.web.controller.AiController;
import com.darkmusic.aiforgotthesecards.web.controller.UserController;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SiteConfigurer implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("index.html");
        registry.addViewController("/home").setViewName("index.html");
        registry.addViewController("/api/ai").setViewName(AiController.class.getName());
        registry.addViewController("/api/user").setViewName(UserController.class.getName());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins("http://localhost:8086", "http://localhost:9090");
    }
}
