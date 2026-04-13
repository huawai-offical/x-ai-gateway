package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AnthropicMessagesEncoder {

    private final ObjectMapper objectMapper;

    public AnthropicMessagesEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnthropicMessagesResponse encode(GatewayResponse response) {
        return AnthropicMessagesResponse.from(response);
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

    private Flux<String> encodeEvent(GatewayStreamEvent event) {
        if (event.type() == GatewayStreamEventType.TEXT_DELTA && event.textDelta() != null && !event.textDelta().isBlank()) {
            return Flux.just(encode("content_block_delta", AnthropicMessagesResponse.contentBlockDelta(event.textDelta())));
        }
        if (event.type() == GatewayStreamEventType.COMPLETED) {
            return Flux.just(
                    encode("message_delta", AnthropicMessagesResponse.messageDelta(event.usage(), toStopReason(event.finishReason()))),
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
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 Anthropic 响应。", exception);
        }
    }
}
