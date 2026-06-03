package com.czar.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the in-memory rate limiting filter in isolation.
 * Uses WebTestClient against a random-port gateway context (no JWT — tests use permitAll).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RateLimitingFilterTest {

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void resetCounters() throws Exception {
        Field field = RateLimitingFilter.class.getDeclaredField("counters");
        field.setAccessible(true);
        ((ConcurrentHashMap<?, ?>) field.get(rateLimitingFilter)).clear();
    }

    @Test
    void firstRequestWithinLimit_isNotRateLimited() throws Exception {
        // After reset, the first call should go through
        assertThat(invokeIsRateLimited(rateLimitingFilter, "test-ip", 200)).isFalse();
    }

    @Test
    void requestExceedingLimit_isRateLimited() throws Exception {
        // Exhaust the limit
        for (int i = 0; i < 200; i++) {
            invokeIsRateLimited(rateLimitingFilter, "limit-ip", 200);
        }
        // 201st request should be rate-limited
        assertThat(invokeIsRateLimited(rateLimitingFilter, "limit-ip", 200)).isTrue();
    }

    @Test
    void differentIps_haveIndependentCounters() throws Exception {
        // Fill up ip-a's bucket
        for (int i = 0; i < 200; i++) {
            invokeIsRateLimited(rateLimitingFilter, "ip-a", 200);
        }
        // ip-b should still be fine
        assertThat(invokeIsRateLimited(rateLimitingFilter, "ip-b", 200)).isFalse();
    }

    @Test
    void windowExpiry_resetsCounter() throws Exception {
        // Exhaust the limit
        for (int i = 0; i < 200; i++) {
            invokeIsRateLimited(rateLimitingFilter, "expiry-ip", 200);
        }
        assertThat(invokeIsRateLimited(rateLimitingFilter, "expiry-ip", 200)).isTrue();

        // Manually age the window by setting the window start to >60s ago
        Field field = RateLimitingFilter.class.getDeclaredField("counters");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, long[]> counters = (ConcurrentHashMap<String, long[]>) field.get(rateLimitingFilter);
        counters.get("global:expiry-ip")[0] = System.currentTimeMillis() - 61_000L;

        // Should be reset in the next call
        assertThat(invokeIsRateLimited(rateLimitingFilter, "expiry-ip", 200)).isFalse();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean invokeIsRateLimited(RateLimitingFilter filter, String ip, long limit) throws Exception {
        java.lang.reflect.Method m = RateLimitingFilter.class.getDeclaredMethod("isRateLimited", String.class, long.class);
        m.setAccessible(true);
        return (boolean) m.invoke(filter, "global:" + ip, limit);
    }
}
