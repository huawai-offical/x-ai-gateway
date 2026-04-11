package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.JsonNode;

public record GatewayToolDefinition(
        String name,
        String description,
        JsonNode inputSchema,
        Boolean strict
) {
}
