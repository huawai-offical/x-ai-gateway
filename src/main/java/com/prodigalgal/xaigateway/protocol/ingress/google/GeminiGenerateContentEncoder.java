package com.prodigalgal.xaigateway.protocol.ingress.google;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionStreamResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalGatewayResponseMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class GeminiGenerateContentEncoder {

    private final ObjectMapper objectMapper;
    private final CanonicalGatewayResponseMapper canonicalGatewayResponseMapper = new CanonicalGatewayResponseMapper();

    public GeminiGenerateContentEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeminiGenerateContentResponse encode(GatewayResponse response) {
        CanonicalResponse canonicalResponse = canonicalGatewayResponseMapper.toCanonicalResponse(response);
        return GeminiGenerateContentResponse.fromCanonical(canonicalResponse);
    }

    public GeminiGenerateContentResponse encode(CanonicalExecutionResult response) {
        return GeminiGenerateContentResponse.fromCanonical(response.response());
    }

    public Flux<String> encodeStream(GatewayStreamResponse response) {
        return response.events().concatMap(this::encodeEvent);
    }

    public Flux<String> encodeStream(CanonicalExecutionStreamResult response) {
        return response.events().concatMap(this::encodeCanonicalEvent);
    }

    private Flux<String> encodeEvent(com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEvent event) {
        CanonicalStreamEvent canonicalEvent = canonicalGatewayResponseMapper.toCanonicalStreamEvent(event);
        return encodeCanonicalEvent(canonicalEvent);
    }

    private Flux<String> encodeCanonicalEvent(CanonicalStreamEvent canonicalEvent) {
        if (canonicalEvent.type() == CanonicalStreamEventType.TEXT_DELTA && canonicalEvent.textDelta() != null && !canonicalEvent.textDelta().isBlank()) {
            return Flux.just(encode(GeminiGenerateContentResponse.fromCanonical(
                    new CanonicalResponse(null, null, canonicalEvent.textDelta(), null, java.util.List.of(), com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage.empty(), null)
            )));
        }
        if (canonicalEvent.type() == CanonicalStreamEventType.TOOL_CALLS && canonicalEvent.toolCalls() != null && !canonicalEvent.toolCalls().isEmpty()) {
            return Flux.just(encode(GeminiGenerateContentResponse.fromCanonical(
                    new CanonicalResponse(null, null, null, null, canonicalEvent.toolCalls(), com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage.empty(), null)
            )));
        }
        if (canonicalEvent.type() == CanonicalStreamEventType.COMPLETED) {
            return Flux.just(encode(GeminiGenerateContentResponse.fromCanonical(
                    new CanonicalResponse(null, null, canonicalEvent.outputText(), canonicalEvent.reasoning(), java.util.List.of(), canonicalEvent.usage(), canonicalEvent.finishReason())
            )));
        }
        return Flux.empty();
    }

    private String encode(Object payload) {
        try {
            return "data: " + objectMapper.writeValueAsString(payload) + "\n\n";
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化 Gemini 响应。", exception);
        }
    }
}
