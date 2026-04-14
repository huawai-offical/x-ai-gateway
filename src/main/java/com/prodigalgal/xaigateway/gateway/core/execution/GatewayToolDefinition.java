package com.prodigalgal.xaigateway.gateway.core.execution;

import tools.jackson.databind.JsonNode;

public record GatewayToolDefinition(
        String name,
        String description,
        JsonNode inputSchema,
        Boolean strict
) {
}
