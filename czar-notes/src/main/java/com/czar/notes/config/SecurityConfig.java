package com.czar.notes.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.security.interfaces.RSAPublicKey;

/**
 * Security config for czar-notes.
 * When jwt.public-key-path is set: adds NotesJwtFilter + requires auth on /api/**
 * When absent (tests): all requests permitted, no filter added.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired(required = false)
    private RSAPublicKey rsaPublicKey;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable());

        if (rsaPublicKey != null) {
            NotesJwtFilter jwtFilter = new NotesJwtFilter(rsaPublicKey);
            http
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/actuator/info",
                            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                    .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }
}

