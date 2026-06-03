package com.czar.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window rate limiter (GlobalFilter).
 *
 * Limits:
 *   - /auth/**   → 10 req/min per IP  (auth endpoints)
 *   - /api/v1/voice/** → 60 req/min per IP (Groq tier guard)
 *   - all others → 200 req/min per IP (global)
 *
 * Window resets after 60 seconds. Returns HTTP 429 with Retry-After: 60 when exceeded.
 */
@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private static final long GLOBAL_LIMIT = 200;
    private static final long AUTH_LIMIT    = 10;
    private static final long VOICE_LIMIT   = 60;
    private static final long WINDOW_MS     = 60_000L;

    // key → [windowStartMs, count]
    private final ConcurrentHashMap<String, long[]> counters = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip   = resolveClientIp(exchange);
        String path = exchange.getRequest().getPath().value();

        String bucketKey;
        long limit;

        if (path.startsWith("/auth/")) {
            bucketKey = "auth:" + ip;
            limit     = AUTH_LIMIT;
        } else if (path.startsWith("/api/v1/voice/")) {
            bucketKey = "voice:" + ip;
            limit     = VOICE_LIMIT;
        } else {
            bucketKey = "global:" + ip;
            limit     = GLOBAL_LIMIT;
        }

        if (isRateLimited(bucketKey, limit)) {
            log.warn("Rate limit exceeded: bucket={} path={}", bucketKey, path);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().set("Retry-After", "60");
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    private boolean isRateLimited(String key, long limit) {
        long now = System.currentTimeMillis();
        long[] slot = counters.compute(key, (k, existing) -> {
            if (existing == null || now - existing[0] >= WINDOW_MS) {
                return new long[]{now, 1L};
            }
            existing[1]++;
            return existing;
        });
        return slot[1] > limit;
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        // Run just after logging (HIGHEST_PRECEDENCE) but before routing
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
