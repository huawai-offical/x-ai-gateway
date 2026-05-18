package com.prodigalgal.xaigateway.infra.config;

import com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiRealtimeWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class OpenAiRealtimeWebSocketConfigurationTests {

    @Test
    void shouldMapOpenAiRealtimeWebSocketRoot() {
        OpenAiRealtimeWebSocketHandler handler = mock(OpenAiRealtimeWebSocketHandler.class);
        HandlerMapping mapping = new OpenAiRealtimeWebSocketConfiguration()
                .openAiRealtimeWebSocketHandlerMapping(handler);

        SimpleUrlHandlerMapping simpleMapping = assertInstanceOf(SimpleUrlHandlerMapping.class, mapping);
        assertSame(handler, simpleMapping.getUrlMap().get("/v1/realtime"));
    }
}
