package com.prodigalgal.xaigateway.gateway.core.canonical;

import tools.jackson.databind.JsonNode;

public record CanonicalReasoningConfig(
        JsonNode rawSettings,
        String effort
) {
}
