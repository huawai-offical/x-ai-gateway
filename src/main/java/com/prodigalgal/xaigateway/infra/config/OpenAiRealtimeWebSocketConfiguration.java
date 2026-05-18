package com.prodigalgal.xaigateway.infra.config;

import com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiRealtimeWebSocketHandler;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

@Configuration(proxyBeanMethods = false)
public class OpenAiRealtimeWebSocketConfiguration {

    @Bean
    HandlerMapping openAiRealtimeWebSocketHandlerMapping(OpenAiRealtimeWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        mapping.setUrlMap(Map.of("/v1/realtime", handler));
        return mapping;
    }
}
