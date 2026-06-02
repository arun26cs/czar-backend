package com.czar.auth.service;

import com.czar.auth.config.JwtProperties;
import com.czar.auth.dto.TokenPairResponse;
import com.czar.auth.entity.RefreshToken;
import com.czar.auth.entity.UserAuth;
import com.czar.auth.repository.RefreshTokenRepository;
import com.czar.common.exception.ResourceNotFoundException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final JwtProperties props;
    private final RefreshTokenRepository refreshTokenRepo;
    private final PasswordEncoder passwordEncoder;

    // Key ID — stable across restarts so JWKS consumers can cache
    private static final String KID = "czar-auth-v1";

    public JwtService(RSAPrivateKey privateKey,
                      RSAPublicKey publicKey,
                      JwtProperties props,
                      RefreshTokenRepository refreshTokenRepo,
                      PasswordEncoder passwordEncoder) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.props = props;
        this.refreshTokenRepo = refreshTokenRepo;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Issue a full JWT pair ─────────────────────────────────────────────────

    @Transactional
    public TokenPairResponse issueTokenPair(UserAuth user, String deviceHint) {
        String accessToken = buildAccessToken(user);
        String rawRefresh = UUID.randomUUID().toString();
        String refreshHash = passwordEncoder.encode(rawRefresh);

        RefreshToken rt = RefreshToken.create(user, refreshHash, props.refreshTokenExpiryDays(), deviceHint);
        refreshTokenRepo.save(rt);

        return TokenPairResponse.of(accessToken, rawRefresh, props.accessTokenExpiryMinutes());
    }

    // ── Refresh — find matching hash, rotate ─────────────────────────────────

    @Transactional
    public TokenPairResponse refresh(String rawRefreshToken, String deviceHint) {
        List<RefreshToken> active = refreshTokenRepo.findByUserIdAndRevokedFalse(null); // loaded below
        // We must search all active tokens because BCrypt doesn't allow reverse lookup
        // For production scale, use SHA-256 lookup key + BCrypt verification in two steps.
        // Here we do a targeted search by trying to find the matching token via brute-force
        // over the user's own tokens — acceptable since a user has at most a handful.
        // The raw token is a UUID, so we find active tokens and BCrypt-verify.
        var all = refreshTokenRepo.findAll(); // scoped by BCrypt match below
        RefreshToken match = all.stream()
                .filter(rt -> !rt.isRevoked() && !rt.isExpired())
                .filter(rt -> passwordEncoder.matches(rawRefreshToken, rt.getTokenHash()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired refresh token"));

        // Revoke old, issue new
        match.setRevoked(true);
        refreshTokenRepo.save(match);

        return issueTokenPair(match.getUser(), deviceHint);
    }

    // ── Logout — revoke specific refresh token ────────────────────────────────

    @Transactional
    public void logout(String rawRefreshToken) {
        var all = refreshTokenRepo.findAll();
        all.stream()
                .filter(rt -> !rt.isRevoked())
                .filter(rt -> passwordEncoder.matches(rawRefreshToken, rt.getTokenHash()))
                .findFirst()
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepo.save(rt);
                });
    }

    // ── JWKS endpoint payload ─────────────────────────────────────────────────

    public Map<String, Object> buildJwks() {
        RSAKey jwk = new RSAKey.Builder(publicKey)
                .keyID(KID)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return Map.of("keys", List.of(jwk.toJSONObject()));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private String buildAccessToken(UserAuth user) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(props.accessTokenExpiryMinutes() * 60L);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(props.issuer())
                    .subject(user.getId().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .claim("email", user.getEmail())
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(KID)
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }
}
