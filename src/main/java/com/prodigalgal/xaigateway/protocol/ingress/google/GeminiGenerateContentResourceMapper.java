package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class GeminiGenerateContentResourceMapper {

    private final ObjectMapper objectMapper;

    public GeminiGenerateContentResourceMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CanonicalResourceRequest toImageGenerationRequest(
            AuthenticatedDistributedKey distributedKey,
            String model,
            GeminiGenerateContentRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("prompt", aggregatePrompt(request));
        Integer candidateCount = optionalInt(request == null ? null : request.generationConfig(), "candidateCount");
        if (candidateCount != null && candidateCount > 0) {
            payload.put("n", candidateCount);
        }
        return buildRequest(
                distributedKey,
                model,
                "/v1/images/generations",
                TranslationResourceType.IMAGE,
                TranslationOperation.IMAGE_GENERATION,
                payload,
                false
        );
    }

    public CanonicalResourceRequest toAudioSpeechRequest(
            AuthenticatedDistributedKey distributedKey,
            String model,
            GeminiGenerateContentRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("input", aggregatePrompt(request));
        payload.put("voice", resolveVoiceName(request));
        Double temperature = optionalDouble(request == null ? null : request.generationConfig(), "temperature");
        if (temperature != null) {
            payload.put("temperature", temperature);
        }
        return buildRequest(
                distributedKey,
                model,
                "/v1/audio/speech",
                TranslationResourceType.AUDIO,
                TranslationOperation.AUDIO_SPEECH,
                payload,
                true
        );
    }

    private CanonicalResourceRequest buildRequest(
            AuthenticatedDistributedKey distributedKey,
            String model,
            String normalizedPath,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            JsonNode requestBody,
            boolean expectsBinary) {
        String requestPath = "/v1beta/models/" + model + ":generateContent";
        return new CanonicalResourceRequest(
                distributedKey.keyPrefix(),
                CanonicalIngressProtocol.GOOGLE_NATIVE,
                "POST",
                requestPath,
                normalizedPath,
                Map.of("model", model),
                model,
                resourceType,
                operation,
                requestBody,
                Map.of(),
                List.of(),
                expectsBinary,
                false
        );
    }

    private String aggregatePrompt(GeminiGenerateContentRequest request) {
        StringBuilder builder = new StringBuilder();
        appendText(builder, request == null ? null : request.systemInstruction());
        JsonNode contents = request == null ? null : request.contents();
        if (contents != null && contents.isArray()) {
            for (JsonNode content : contents) {
                JsonNode parts = content.path("parts");
                if (!parts.isArray()) {
                    continue;
                }
                for (JsonNode part : parts) {
                    appendText(builder, part);
                }
            }
        }
        if (builder.isEmpty()) {
            throw new IllegalArgumentException("generateContent resource-mode 至少需要一条 text 输入。");
        }
        return builder.toString();
    }

    private void appendText(StringBuilder builder, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        String text = node.path("text").asText(null);
        if (text == null || text.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append(text);
    }

    private String resolveVoiceName(GeminiGenerateContentRequest request) {
        String value = request == null
                ? null
                : request.generationConfig()
                .path("speechConfig")
                .path("voiceConfig")
                .path("prebuiltVoiceConfig")
                .path("voiceName")
                .asText(null);
        return value == null || value.isBlank() ? "Kore" : value;
    }

    private Integer optionalInt(JsonNode node, String fieldName) {
        if (node == null || node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
            return null;
        }
        return node.path(fieldName).asInt();
    }

    private Double optionalDouble(JsonNode node, String fieldName) {
        if (node == null || node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
            return null;
        }
        return node.path(fieldName).asDouble();
    }
}
