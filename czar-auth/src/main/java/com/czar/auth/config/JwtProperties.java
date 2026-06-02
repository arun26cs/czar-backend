package com.czar.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration bound from application.yml under the `jwt` prefix.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String issuer,
        int accessTokenExpiryMinutes,
        int refreshTokenExpiryDays,
        String privateKeyPath,
        String publicKeyPath
) {}
