package com.czar.user.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads the RSA public key from a PEM file.
 * Only activated when jwt.public-key-path is set in application.yml.
 * Tests omit this property → bean is absent → SecurityConfig falls back to permitAll.
 */
@Configuration
@ConditionalOnProperty(name = "jwt.public-key-path")
public class JwtConfig {

    private final String publicKeyPath;
    private final ResourceLoader resourceLoader;

    public JwtConfig(
            @org.springframework.beans.factory.annotation.Value("${jwt.public-key-path}") String publicKeyPath,
            ResourceLoader resourceLoader) {
        this.publicKeyPath = publicKeyPath;
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public RSAPublicKey rsaPublicKey() throws Exception {
        var resource = resourceLoader.getResource(publicKeyPath);
        String raw = new String(resource.getInputStream().readAllBytes());
        String pem = raw
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(decoded));
    }
}
