package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

public record GeminiGenerateContentRequest(
        JsonNode contents,
        JsonNode systemInstruction,
        JsonNode generationConfig,
        JsonNode tools,
        JsonNode toolConfig,
        @JsonProperty("x_ai_gateway_allow_google_maps")
        Boolean allowGoogleMaps
) {
}
