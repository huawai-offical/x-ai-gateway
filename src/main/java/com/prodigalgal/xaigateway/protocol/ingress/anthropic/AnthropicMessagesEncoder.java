package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionStreamResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AnthropicMessagesEncoder {

    private final ObjectMapper objectMapper;

    public AnthropicMessagesEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnthropicMessagesResponse encode(CanonicalExecutionResult response) {
        return AnthropicMessagesResponse.fromCanonical(response.response());
    }

    public Flux<String> encodeStream(CanonicalExecutionStreamResult response) {
        return Flux.concat(
                Flux.just(encode("message_start", AnthropicMessagesResponse.messageStart(
                        response.routeSelection().publicModel(),
                        CanonicalUsage.empty()
                ))),
                Flux.just(encode("content_block_start", AnthropicMessagesResponse.contentBlockStart())),
                response.events().concatMap(this::encodeCanonicalEvent),
                Flux.just(encode("content_block_stop", AnthropicMessagesResponse.contentBlockStop()))
        );
    }

    private Flux<String> encodeCanonicalEvent(CanonicalStreamEvent canonicalEvent) {
        if (canonicalEvent.type() == CanonicalStreamEventType.TEXT_DELTA && canonicalEvent.textDelta() != null && !canonicalEvent.textDelta().isBlank()) {
            return Flux.just(encode("content_block_delta", AnthropicMessagesResponse.contentBlockDelta(canonicalEvent.textDelta())));
        }
        if (canonicalEvent.type() == CanonicalStreamEventType.COMPLETED) {
            return Flux.just(
                    encode("message_delta", AnthropicMessagesResponse.messageDelta(canonicalEvent.usage(), toStopReason(canonicalEvent.finishReason()))),
                    encode("message_stop", AnthropicMessagesResponse.messageStop())
            );
        }
        return Flux.empty();
    }

    private String toStopReason(GatewayFinishReason finishReason) {
        if (finishReason == null) {
            return "end_turn";
        }
        return switch (finishReason) {
            case TOOL_CALLS -> "tool_use";
            case LENGTH, MAX_TOKENS -> "max_tokens";
            case CONTENT_FILTER -> "refusal";
            case STOP, END_TURN, CANCELED, ERROR, UNKNOWN -> "end_turn";
        };
    }

    private String encode(String event, Object payload) {
        try {
            return "event: " + event + "\n" + "data: " + objectMapper.writeValueAsString(payload) + "\n\n";
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化 Anthropic 响应。", exception);
        }
    }
}
