package com.czar.gateway.config;

import com.czar.gateway.websocket.GatewayWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

/**
 * Registers the reactive WebSocket endpoint at {@code /ws}.
 *
 * <p>Connection URL: {@code ws://host/ws?token=eyJ...}
 * The JWT is validated inside {@link GatewayWebSocketHandler}.
 *
 * <p>Order = 1 to take priority over Spring Cloud Gateway's route predicates.
 */
@Configuration
public class WebSocketConfig {

    private final GatewayWebSocketHandler webSocketHandler;

    public WebSocketConfig(GatewayWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/ws", webSocketHandler));
        mapping.setOrder(1); // higher priority than gateway routes
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
