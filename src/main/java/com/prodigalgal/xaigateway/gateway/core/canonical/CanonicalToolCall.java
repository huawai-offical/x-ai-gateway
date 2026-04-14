package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;

public record CanonicalToolCall(
        String id,
        String type,
        String name,
        String arguments
) {

    public static CanonicalToolCall from(GatewayToolCall toolCall) {
        if (toolCall == null) {
            return null;
        }
        return new CanonicalToolCall(toolCall.id(), toolCall.type(), toolCall.name(), toolCall.arguments());
    }

    public GatewayToolCall toGatewayToolCall() {
        return new GatewayToolCall(id, type, name, arguments);
    }
}
