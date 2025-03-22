package com.darkmusic.aiforgotthesecards.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Profile("prod")
@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class ProdSecurityConfig {
}
