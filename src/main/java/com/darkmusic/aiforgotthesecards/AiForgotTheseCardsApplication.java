package com.darkmusic.aiforgotthesecards;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.DefaultLoginPageConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

import com.darkmusic.aiforgotthesecards.business.entities.services.CustomUserDetailsService;
import com.darkmusic.aiforgotthesecards.config.BcryptCompatPasswordEncoder;

import jakarta.servlet.http.HttpServletResponse;

@SpringBootApplication
public class AiForgotTheseCardsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiForgotTheseCardsApplication.class, args);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BcryptCompatPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CustomUserDetailsService userDetailsService,
                                                   PasswordEncoder passwordEncoder
    ) throws Exception {
        HttpSecurity httpSecurity = http
                .authorizeHttpRequests(requests -> requests
                        // Permit SPA assets and root
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon*", "/vite*").permitAll()
                        // Permit routes handled by the SPA router
                        .requestMatchers("/login", "/logout").permitAll()
                        // Permit specific API endpoints for authentication
                        .requestMatchers("/api/csrf").permitAll()
                        // Permit the Springdoc API path
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        // Permit actuator endpoints for monitoring and Swagger
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        // Secure all other API endpoints
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Any other non-API request should serve the SPA shell
                        .anyRequest().permitAll())
                .headers((headers) -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .cors(Customizer.withDefaults())
                .userDetailsService(userDetailsService)
                // For API calls, return 401 instead of redirecting
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                (request, response, authException) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED),
                                new RegexRequestMatcher("^/api/.*", null)
                        ))
                .formLogin(form -> form
                    // Serve the login page from the SPA (prevents Spring Security default login page)
                    .loginPage("/login")
                    // The backend endpoint that processes the login POST request
                    .loginProcessingUrl("/api/login")
                    // On successful login, return 200 OK, not a redirect
                    .successHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK))
                    // On failure, return 401 Unauthorized
                    .failureHandler((req, res, ex) -> res.setStatus(HttpServletResponse.SC_UNAUTHORIZED)))
                .logout(
                    logout -> logout
                        .logoutUrl("/api/logout")
                        .invalidateHttpSession(true)
                        .addLogoutHandler(
                            new CookieClearingLogoutHandler("JSESSIONID", "XSRF-TOKEN"))
                        .logoutSuccessHandler((req,res,auth) -> res.setStatus(HttpServletResponse.SC_NO_CONTENT)));

        DefaultLoginPageConfigurer<HttpSecurity> defaultLoginPage = httpSecurity.getConfigurer(DefaultLoginPageConfigurer.class);
        if (defaultLoginPage != null) {
            defaultLoginPage.disable();
        }

        return http.build();
    }
}
