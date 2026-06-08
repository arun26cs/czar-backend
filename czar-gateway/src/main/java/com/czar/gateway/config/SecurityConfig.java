package com.czar.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * Reactive security config for czar-gateway (Spring Cloud Gateway / WebFlux).
 *
 * When jwt.public-key-path is configured:
 *   - RS256 JWT validation on all /api/** routes via Spring oauth2ResourceServer
 *   - /auth/** and /actuator/** are public
 *
 * When key is absent (tests / local without keys):
 *   - All requests permitted — downstream services enforce their own JWT checks
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Autowired(required = false)
    private RSAPublicKey rsaPublicKey;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);

        if (rsaPublicKey != null) {
            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                    .withPublicKey(rsaPublicKey).build();
            http
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(decoder)))
                .authorizeExchange(ex -> ex
                    .pathMatchers("/auth/**", "/actuator/health", "/actuator/info", "/ws").permitAll()
                    .pathMatchers("/api/**").authenticated()
                    .anyExchange().permitAll()
                );
        } else {
            http.authorizeExchange(ex -> ex.anyExchange().permitAll());
        }

        return http.build();
    }

    /**
     * CORS policy — allows the Czar web app and local development origins.
     * Production: only prodczar.com; local dev: localhost:3000 / localhost:8080.
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "https://prodczar.com",
                "https://www.prodczar.com",
                "http://localhost:3000",
                "http://localhost:8080"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Device-Hint", "X-Service-Token"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
