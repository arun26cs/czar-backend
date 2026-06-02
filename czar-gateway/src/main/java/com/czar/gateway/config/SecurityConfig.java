package com.czar.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Phase 1 scaffold security config — permits all traffic through the gateway.
 *
 * Phase 9 will replace this with:
 *  - JWT validation WebFilter (fetches JWKS from czar-auth on startup)
 *  - Route-level allow/deny rules (public: /auth/**, protected: /api/**)
 *  - CORS restricted to prodczar.com
 *  - Rate limiting filters
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
