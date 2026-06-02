package com.czar.notes.config;

import com.czar.common.security.JwtValidationFilter;

import java.security.interfaces.RSAPublicKey;

/**
 * Concrete JWT validation filter for czar-notes.
 */
public class NotesJwtFilter extends JwtValidationFilter {

    private final RSAPublicKey publicKey;

    public NotesJwtFilter(RSAPublicKey publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    protected RSAPublicKey getPublicKey() {
        return publicKey;
    }
}
