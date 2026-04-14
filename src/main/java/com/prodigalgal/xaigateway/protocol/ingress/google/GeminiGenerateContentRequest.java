package com.prodigalgal.xaigateway.protocol.ingress.google;

import tools.jackson.databind.JsonNode;

public record GeminiGenerateContentRequest(
        JsonNode contents,
        JsonNode systemInstruction,
        JsonNode generationConfig,
        JsonNode tools,
        JsonNode toolConfig
) {
}
