package com.czar.user.config;

import com.czar.common.security.JwtValidationFilter;

import java.security.interfaces.RSAPublicKey;

/**
 * Concrete JWT validation filter for czar-user.
 * Delegates to the shared RSA public key loaded by JwtConfig.
 */
public class UserJwtFilter extends JwtValidationFilter {

    private final RSAPublicKey publicKey;

    public UserJwtFilter(RSAPublicKey publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    protected RSAPublicKey getPublicKey() {
        return publicKey;
    }
}
