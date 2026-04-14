package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalGatewayResponseMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AnthropicMessagesEncoder {

    private final ObjectMapper objectMapper;
    private final CanonicalGatewayResponseMapper canonicalGatewayResponseMapper = new CanonicalGatewayResponseMapper();

    public AnthropicMessagesEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnthropicMessagesResponse encode(GatewayResponse response) {
        return AnthropicMessagesResponse.fromCanonical(canonicalGatewayResponseMapper.toCanonicalResponse(response));
    }

    public Flux<String> encodeStream(GatewayStreamResponse response) {
        return Flux.concat(
                Flux.just(encode("message_start", AnthropicMessagesResponse.messageStart(
                        response.routeSelection().publicModel(),
                        com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage.empty()
                ))),
                Flux.just(encode("content_block_start", AnthropicMessagesResponse.contentBlockStart())),
                response.events().concatMap(this::encodeEvent),
                Flux.just(encode("content_block_stop", AnthropicMessagesResponse.contentBlockStop()))
        );
    }

    private Flux<String> encodeEvent(com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEvent event) {
        CanonicalStreamEvent canonicalEvent = canonicalGatewayResponseMapper.toCanonicalStreamEvent(event);
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
