package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionStreamResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import java.time.Instant;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

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
        return encodeStream(response, null);
    }

    public Flux<String> encodeStream(CanonicalExecutionStreamResult response, JsonNode streamOptions) {
        String streamId = "chatcmpl-" + response.requestId();
        long created = Instant.now().getEpochSecond();
        boolean includeUsage = includeUsage(streamOptions);
        String publicModel = response.routeSelection().publicModel();
        return Flux.concat(
                Flux.just(encode(OpenAiChatCompletionResponse.roleChunk(streamId, created, publicModel))),
                response.events().concatMap(event -> encodeEvent(publicModel, streamId, created, includeUsage, event)),
                Flux.just("data: [DONE]\n\n")
        );
    }

    private Flux<String> encodeEvent(
            String publicModel,
            String streamId,
            long created,
            boolean includeUsage,
            CanonicalStreamEvent canonicalEvent) {
        if (canonicalEvent.type() == CanonicalStreamEventType.TEXT_DELTA && canonicalEvent.textDelta() != null && !canonicalEvent.textDelta().isBlank()) {
            return Flux.just(encode(OpenAiChatCompletionResponse.contentChunk(
                    streamId,
                    created,
                    publicModel,
                    canonicalEvent.textDelta()
            )));
        }
        if (canonicalEvent.type() == CanonicalStreamEventType.TOOL_CALLS && canonicalEvent.toolCalls() != null && !canonicalEvent.toolCalls().isEmpty()) {
            return Flux.just(encode(OpenAiChatCompletionResponse.toolCallChunkCanonical(
                    streamId,
                    created,
                    publicModel,
                    canonicalEvent.toolCalls()
            )));
        }
        if (canonicalEvent.type() == CanonicalStreamEventType.COMPLETED) {
            String finishChunk = encode(OpenAiChatCompletionResponse.finishChunk(
                    streamId,
                    created,
                    publicModel,
                    toFinishReason(canonicalEvent.finishReason())
            ));
            if (includeUsage) {
                return Flux.just(
                        finishChunk,
                        encode(OpenAiChatCompletionResponse.usageChunk(
                                streamId,
                                created,
                                publicModel,
                                canonicalEvent.usage()
                        ))
                );
            }
            return Flux.just(finishChunk);
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

    private boolean includeUsage(JsonNode streamOptions) {
        return streamOptions != null
                && streamOptions.isObject()
                && streamOptions.path("include_usage").asBoolean(false);
    }
}
