package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionStreamResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class OpenAiChatCompletionEncoder {

    private final ObjectMapper objectMapper;

    public OpenAiChatCompletionEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OpenAiChatCompletionResponse encode(CanonicalExecutionResult response) {
        return OpenAiChatCompletionResponse.fromCanonical(response.response());
    }

    public Flux<String> encodeStream(CanonicalExecutionStreamResult response) {
        return Flux.concat(
                Flux.just(encode(OpenAiChatCompletionResponse.roleChunk(response.routeSelection().publicModel()))),
                response.events().concatMap(event -> encodeEvent(response.routeSelection().publicModel(), event)),
                Flux.just("data: [DONE]\n\n")
        );
    }

    private Flux<String> encodeEvent(String publicModel, CanonicalStreamEvent canonicalEvent) {
        if (canonicalEvent.type() == CanonicalStreamEventType.TEXT_DELTA && canonicalEvent.textDelta() != null && !canonicalEvent.textDelta().isBlank()) {
            return Flux.just(encode(OpenAiChatCompletionResponse.contentChunk(
                    publicModel,
                    canonicalEvent.textDelta()
            )));
        }
        if (canonicalEvent.type() == CanonicalStreamEventType.TOOL_CALLS && canonicalEvent.toolCalls() != null && !canonicalEvent.toolCalls().isEmpty()) {
            return Flux.just(encode(OpenAiChatCompletionResponse.toolCallChunkCanonical(
                    publicModel,
                    canonicalEvent.toolCalls()
            )));
        }
        if (canonicalEvent.type() == CanonicalStreamEventType.COMPLETED) {
            return Flux.just(encode(OpenAiChatCompletionResponse.finishChunk(
                    publicModel,
                    toFinishReason(canonicalEvent.finishReason())
            )));
        }
        return Flux.empty();
    }

    private String toFinishReason(GatewayFinishReason finishReason) {
        if (finishReason == null) {
            return "stop";
        }
        return switch (finishReason) {
            case TOOL_CALLS -> "tool_calls";
            case LENGTH, MAX_TOKENS -> "length";
            case CONTENT_FILTER -> "content_filter";
            case STOP, END_TURN, CANCELED, ERROR, UNKNOWN -> "stop";
        };
    }

    private String encode(Object payload) {
        try {
            return "data: " + objectMapper.writeValueAsString(payload) + "\n\n";
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化 OpenAI Chat Completions 响应。", exception);
        }
    }
}
