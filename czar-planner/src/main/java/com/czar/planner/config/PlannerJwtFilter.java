package com.czar.planner.config;

import com.czar.common.security.JwtValidationFilter;

import java.security.interfaces.RSAPublicKey;

/**
 * Concrete JWT validation filter for czar-planner.
 */
public class PlannerJwtFilter extends JwtValidationFilter {

    private final RSAPublicKey publicKey;

    public PlannerJwtFilter(RSAPublicKey publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    protected RSAPublicKey getPublicKey() {
        return publicKey;
    }
}
