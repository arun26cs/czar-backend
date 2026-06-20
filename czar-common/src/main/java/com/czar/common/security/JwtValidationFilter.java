package com.czar.common.security;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import java.util.Date;

/**
 * Shared RS256 JWT validation filter for all servlet-based services.
 *
 * <p>Usage: extend this class in each service's security config and provide
 * the RSA public key via {@link #getPublicKey()}. The validated {@code sub}
 * claim is stored as the Spring Security principal (userId UUID string).
 *
 * <p>The reactive gateway uses a separate WebFilter (added in Phase 9).
 */
public abstract class JwtValidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtValidationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWSVerifier verifier = new RSASSAVerifier(getPublicKey());

            if (!jwt.verify(verifier)) {
                log.warn("JWT signature verification failed for request: {} {}", request.getMethod(), request.getRequestURI());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT signature");
                return;
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            // Reject expired tokens
            if (claims.getExpirationTime() != null
                    && claims.getExpirationTime().before(new Date())) {
                log.warn("JWT expired at {} for request: {} {}", claims.getExpirationTime(), request.getMethod(), request.getRequestURI());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "JWT has expired");
                return;
            }

            String userId = claims.getSubject();
            if (userId == null || userId.isBlank()) {
                log.warn("JWT missing 'sub' claim for request: {} {}", request.getMethod(), request.getRequestURI());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "JWT missing sub claim");
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            log.warn("JWT validation failed for request: {} {} — reason: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "JWT validation failed");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Returns the RSA public key used to verify JWT signatures.
     * <p>Implementations load this from GCP Secret Manager (prod)
     * or from a local key file / env variable (dev).
     */
    protected abstract RSAPublicKey getPublicKey();
}
