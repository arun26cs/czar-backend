package com.czar.voiceai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.security.interfaces.RSAPublicKey;

/**
 * WebFlux reactive security config for czar-voice-ai.
 * When jwt.public-key-path is set: validates RS256 JWT, requires auth on /api/**.
 * When absent (tests): all requests permitted.
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
            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(rsaPublicKey).build();
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(decoder)));
            http.authorizeExchange(ex -> ex
                    .pathMatchers("/actuator/**", "/swagger-ui.html", "/swagger-ui/**",
                            "/v3/api-docs", "/v3/api-docs/**", "/webjars/**").permitAll()
                    .anyExchange().authenticated());
        } else {
            http.authorizeExchange(ex -> ex.anyExchange().permitAll());
        }

        return http.build();
    }
}

