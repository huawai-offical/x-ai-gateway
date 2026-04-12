package com.prodigalgal.xaigateway.infra.config;

import com.prodigalgal.xaigateway.gateway.core.ops.OpsEventBusService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class OpsRealtimeConfiguration {

    @Bean
    HandlerMapping opsWebSocketHandlerMapping(OpsEventBusService opsEventBusService) {
        WebSocketHandler handler = session -> session.send(
                opsEventBusService.stream().map(session::textMessage)
        );
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        mapping.setUrlMap(Map.of("/admin/ops/ws", handler));
        return mapping;
    }

    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
