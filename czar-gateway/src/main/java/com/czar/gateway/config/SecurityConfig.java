package com.czar.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
     * CORS policy — MUST run before Spring Security (order HIGHEST_PRECEDENCE)
     * so OPTIONS preflight requests are answered with CORS headers before the
     * security filter chain can reject them with 403.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                // Production web
                "https://prodczar.com",
                "https://www.prodczar.com",
                // Local web dev
                "http://localhost:3000",
                "http://localhost:8080",
                "http://localhost:19000",
                "http://localhost:19006",
                // Expo Go on physical device — any local network IP
                "http://172.*.*.*",
                "http://192.168.*.*",
                "http://10.*.*.*",
                // Expo hosted proxy
                "https://expo.io",
                "https://*.expo.io",
                "https://*.expo.dev"
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
