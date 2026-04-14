package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class OpenAiChatCompletionEncoder {

    private final ObjectMapper objectMapper;

    public OpenAiChatCompletionEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OpenAiChatCompletionResponse encode(GatewayResponse response) {
        return OpenAiChatCompletionResponse.from(response);
    }

    public Flux<String> encodeStream(GatewayStreamResponse response) {
        return Flux.concat(
                Flux.just(encode(OpenAiChatCompletionResponse.roleChunk(response.routeSelection().publicModel()))),
                response.events().concatMap(event -> encodeEvent(response, event)),
                Flux.just("data: [DONE]\n\n")
        );
    }

    private Flux<String> encodeEvent(GatewayStreamResponse response, GatewayStreamEvent event) {
        if (event.type() == GatewayStreamEventType.TEXT_DELTA && event.textDelta() != null && !event.textDelta().isBlank()) {
            return Flux.just(encode(OpenAiChatCompletionResponse.contentChunk(
                    response.routeSelection().publicModel(),
                    event.textDelta()
            )));
        }
        if (event.type() == GatewayStreamEventType.TOOL_CALLS && event.toolCalls() != null && !event.toolCalls().isEmpty()) {
            return Flux.just(encode(OpenAiChatCompletionResponse.toolCallChunk(
                    response.routeSelection().publicModel(),
                    event.toolCalls()
            )));
        }
        if (event.type() == GatewayStreamEventType.COMPLETED) {
            return Flux.just(encode(OpenAiChatCompletionResponse.finishChunk(
                    response.routeSelection().publicModel(),
                    toFinishReason(event.finishReason())
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
