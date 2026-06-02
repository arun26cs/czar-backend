package com.czar.voiceai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Phase 1 scaffold — all requests permitted.
 * Phase 7 will add: JWT WebFilter validation, userId extraction, Groq rate-limit guard.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .authorizeExchange(ex -> ex.anyExchange().permitAll())
            .csrf(ServerHttpSecurity.CsrfSpec::disable);
        return http.build();
    }
}
