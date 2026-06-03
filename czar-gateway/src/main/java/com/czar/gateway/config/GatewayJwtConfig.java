package com.czar.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads the RSA public key for reactive JWT validation in the gateway.
 * Only activated when jwt.public-key-path is set (production/staging).
 * Tests omit this property — SecurityConfig falls back to permitAll.
 */
@Configuration
@ConditionalOnProperty(name = "jwt.public-key-path")
public class GatewayJwtConfig {

    private final String publicKeyPath;
    private final ResourceLoader resourceLoader;

    public GatewayJwtConfig(
            @Value("${jwt.public-key-path}") String publicKeyPath,
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
