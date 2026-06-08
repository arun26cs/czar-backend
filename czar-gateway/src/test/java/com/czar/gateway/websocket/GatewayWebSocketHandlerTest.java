package com.czar.gateway.websocket;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Fix 4 — GatewayWebSocketHandler private helper tests.
 *
 * resolveUserId() and extractToken() are tested via ReflectionTestUtils.
 * When rsaPublicKey is null (test mode) signature verification is skipped —
 * only JWT parse + expiry check + UUID sub are validated.
 */
@ExtendWith(MockitoExtension.class)
class GatewayWebSocketHandlerTest {

    @Mock
    private WebSocketSessionRegistry registry;

    @Mock
    private WebSocketEventBroadcaster broadcaster;

    private GatewayWebSocketHandler handler;

    // 32+ byte HMAC secret for signing test JWTs (any key is fine when sig check is skipped)
    private static final String HMAC_SECRET = "czar-test-secret-at-least-32-bytes-long!!";

    @BeforeEach
    void setUp() {
        handler = new GatewayWebSocketHandler(registry, broadcaster);
        // rsaPublicKey stays null → signature verification is skipped in tests
    }

    // ── extractToken ─────────────────────────────────────────────────────────

    @Test
    void extractToken_withTokenParam_returnsToken() {
        String token = invokeExtractToken("token=my.jwt.token");
        assertThat(token).isEqualTo("my.jwt.token");
    }

    @Test
    void extractToken_withMultipleParams_returnsTokenValue() {
        String token = invokeExtractToken("foo=bar&token=my.jwt.value&baz=qux");
        assertThat(token).isEqualTo("my.jwt.value");
    }

    @Test
    void extractToken_noTokenParam_returnsNull() {
        String token = invokeExtractToken("foo=bar&baz=qux");
        assertThat(token).isNull();
    }

    @Test
    void extractToken_nullQuery_returnsNull() {
        String token = invokeExtractToken(null);
        assertThat(token).isNull();
    }

    // ── resolveUserId ─────────────────────────────────────────────────────────

    @Test
    void resolveUserId_validJwt_returnsUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, Instant.now().plusSeconds(3600));

        UUID resolved = invokeResolveUserId(token);

        assertThat(resolved).isEqualTo(userId);
    }

    @Test
    void resolveUserId_nullToken_returnsNull() {
        UUID resolved = invokeResolveUserId(null);
        assertThat(resolved).isNull();
    }

    @Test
    void resolveUserId_blankToken_returnsNull() {
        UUID resolved = invokeResolveUserId("   ");
        assertThat(resolved).isNull();
    }

    @Test
    void resolveUserId_malformedJwt_returnsNull() {
        UUID resolved = invokeResolveUserId("this.is.not.a.jwt");
        // Should return null — parse error is caught and logged
        assertThat(resolved).isNull();
    }

    @Test
    void resolveUserId_expiredJwt_returnsNull() throws Exception {
        UUID userId = UUID.randomUUID();
        // Expired 1 hour ago
        String token = buildToken(userId, Instant.now().minusSeconds(3600));

        UUID resolved = invokeResolveUserId(token);

        assertThat(resolved).isNull();
    }

    @Test
    void resolveUserId_nonUuidSubject_returnsNull() throws Exception {
        String token = buildTokenWithSub("not-a-uuid", Instant.now().plusSeconds(3600));

        UUID resolved = invokeResolveUserId(token);

        assertThat(resolved).isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String buildToken(UUID userId, Instant expiresAt) throws Exception {
        return buildTokenWithSub(userId.toString(), expiresAt);
    }

    private String buildTokenWithSub(String subject, Instant expiresAt) throws Exception {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new MACSigner(HMAC_SECRET));
        return jwt.serialize();
    }

    private String invokeExtractToken(String query) {
        return ReflectionTestUtils.invokeMethod(handler, "extractToken", query);
    }

    private UUID invokeResolveUserId(String token) {
        return ReflectionTestUtils.invokeMethod(handler, "resolveUserId", token);
    }
}
