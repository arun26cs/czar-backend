package com.czar.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Logs every inbound request with method, path, response status, and elapsed time.
 * Runs at the highest precedence so it wraps all other filters.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start  = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();
        String path   = exchange.getRequest().getPath().value();

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    int status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value() : 0;
                    long ms = System.currentTimeMillis() - start;
                    log.info("{} {} → {} ({}ms)", method, path, status, ms);
                })
                .doOnError(e -> {
                    long ms = System.currentTimeMillis() - start;
                    log.warn("{} {} → ERROR ({}ms): {}", method, path, ms, e.getMessage());
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
