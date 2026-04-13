package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class GeminiGenerateContentEncoder {

    private final ObjectMapper objectMapper;

    public GeminiGenerateContentEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeminiGenerateContentResponse encode(GatewayResponse response) {
        return GeminiGenerateContentResponse.from(response);
    }

    public Flux<String> encodeStream(GatewayStreamResponse response) {
        return response.events().concatMap(this::encodeEvent);
    }

    private Flux<String> encodeEvent(GatewayStreamEvent event) {
        if (event.type() == GatewayStreamEventType.TEXT_DELTA && event.textDelta() != null && !event.textDelta().isBlank()) {
            return Flux.just(encode(GeminiGenerateContentResponse.from(
                    event.textDelta(),
                    com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageView.empty(),
                    List.of()
            )));
        }
        if (event.type() == GatewayStreamEventType.TOOL_CALLS && event.toolCalls() != null && !event.toolCalls().isEmpty()) {
            return Flux.just(encode(GeminiGenerateContentResponse.from(
                    null,
                    com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageView.empty(),
                    event.toolCalls()
            )));
        }
        if (event.type() == GatewayStreamEventType.COMPLETED) {
            return Flux.just(encode(GeminiGenerateContentResponse.from(
                    event.outputText(),
                    event.usage(),
                    List.of()
            )));
        }
        return Flux.empty();
    }

    private String encode(Object payload) {
        try {
            return "data: " + objectMapper.writeValueAsString(payload) + "\n\n";
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 Gemini 响应。", exception);
        }
    }
}
