package com.czar.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 3 security config for czar-auth.
 *
 * All auth endpoints are public — this service IS the authentication boundary.
 * Protected routes: /auth/token/refresh and /auth/logout require a valid refresh token
 * in the request body (validated in service layer), not a Bearer header.
 *
 * OAuth2 login is handled by Spring Security's built-in OAuth2 client support.
 * On success, OAuth2SuccessHandler writes a JWT pair back to the response.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    public SecurityConfig(OAuth2SuccessHandler oAuth2SuccessHandler) {
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/auth/email/**",
                    "/auth/phone/**",
                    "/auth/token/**",
                    "/auth/oauth/google/mobile",
                    "/auth/oauth/github/mobile",
                    "/auth/.well-known/**",
                    "/actuator/health",
                    "/actuator/info"
                ).permitAll()
                .anyRequest().permitAll()   // OAuth2 /login/** handled by Spring Security internally
            )
            // IF_REQUIRED: allows a session only for the OAuth2 state/CSRF handshake.
            // Regular API calls (OTP, token) are stateless — no session is attached to them.
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .csrf(csrf -> csrf.disable())
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oAuth2SuccessHandler)
            );
        return http.build();
    }
}
