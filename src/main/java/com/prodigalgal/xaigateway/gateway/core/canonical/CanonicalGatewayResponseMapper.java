package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEventType;
import java.util.List;

public class CanonicalGatewayResponseMapper {

    public CanonicalResponse toCanonicalResponse(GatewayResponse response) {
        return new CanonicalResponse(
                response.requestId(),
                response.routeSelection().publicModel(),
                response.outputText(),
                response.reasoning(),
                toToolCalls(response.toolCalls()),
                CanonicalUsage.from(response.usage()),
                response.finishReason()
        );
    }

    public CanonicalStreamEvent toCanonicalStreamEvent(GatewayStreamEvent event) {
        return new CanonicalStreamEvent(
                toType(event.type()),
                event.textDelta(),
                event.reasoningDelta(),
                toToolCalls(event.toolCalls()),
                CanonicalUsage.from(event.usage()),
                event.terminal(),
                event.finishReason(),
                event.outputText(),
                event.reasoning()
        );
    }

    private List<CanonicalToolCall> toToolCalls(List<com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream()
                .map(CanonicalToolCall::from)
                .toList();
    }

    private CanonicalStreamEventType toType(GatewayStreamEventType type) {
        if (type == null) {
            return CanonicalStreamEventType.ERROR;
        }
        return switch (type) {
            case TEXT_DELTA -> CanonicalStreamEventType.TEXT_DELTA;
            case REASONING_DELTA -> CanonicalStreamEventType.REASONING_DELTA;
            case TOOL_CALLS -> CanonicalStreamEventType.TOOL_CALLS;
            case COMPLETED -> CanonicalStreamEventType.COMPLETED;
            case ERROR -> CanonicalStreamEventType.ERROR;
        };
    }
}
