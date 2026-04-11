package com.prodigalgal.xaigateway.gateway.core.execution;

public record GatewayToolCall(
        String id,
        String type,
        String name,
        String arguments
) {
}
