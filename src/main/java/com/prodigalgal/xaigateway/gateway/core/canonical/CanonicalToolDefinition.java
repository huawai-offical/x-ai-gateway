package com.prodigalgal.xaigateway.gateway.core.canonical;

import tools.jackson.databind.JsonNode;

public record CanonicalToolDefinition(
        String name,
        String description,
        JsonNode inputSchema,
        Boolean strict
) {
}
