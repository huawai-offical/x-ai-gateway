package com.prodigalgal.xaigateway.protocol.ingress.google;

import tools.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GeminiGenerateContentModeResolver {

    public GeminiGenerateContentMode resolve(GeminiGenerateContentRequest request) {
        Set<String> modalities = responseModalities(request == null ? null : request.generationConfig());
        if (modalities.contains("IMAGE")) {
            return GeminiGenerateContentMode.IMAGE_GENERATION;
        }
        if (modalities.contains("AUDIO") && containsOnlyTextParts(request == null ? null : request.contents())) {
            return GeminiGenerateContentMode.AUDIO_SPEECH;
        }
        return GeminiGenerateContentMode.CHAT;
    }

    private Set<String> responseModalities(JsonNode generationConfig) {
        Set<String> values = new HashSet<>();
        if (generationConfig == null || !generationConfig.path("responseModalities").isArray()) {
            return values;
        }
        for (JsonNode item : generationConfig.path("responseModalities")) {
            String value = item.asText(null);
            if (value != null && !value.isBlank()) {
                values.add(value.trim().toUpperCase());
            }
        }
        return values;
    }

    private boolean containsOnlyTextParts(JsonNode contents) {
        if (contents == null || !contents.isArray()) {
            return false;
        }
        boolean hasText = false;
        for (JsonNode content : contents) {
            JsonNode parts = content.path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                continue;
            }
            for (JsonNode part : parts) {
                if (part.has("text")) {
                    String text = part.path("text").asText(null);
                    if (text != null && !text.isBlank()) {
                        hasText = true;
                    }
                    continue;
                }
                return false;
            }
        }
        return hasText;
    }

    public enum GeminiGenerateContentMode {
        CHAT,
        IMAGE_GENERATION,
        AUDIO_SPEECH
    }
}
