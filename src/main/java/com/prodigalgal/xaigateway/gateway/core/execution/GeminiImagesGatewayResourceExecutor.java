package com.prodigalgal.xaigateway.gateway.core.execution;

import com.google.genai.Client;
import com.google.genai.types.GenerateImagesConfig;
import com.google.genai.types.GenerateImagesResponse;
import com.google.genai.types.GeneratedImage;
import com.google.genai.types.Image;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class GeminiImagesGatewayResourceExecutor implements GatewayResourceExecutor {

    private final GeminiChatModelFactory geminiChatModelFactory;
    private final ObjectMapper objectMapper;

    public GeminiImagesGatewayResourceExecutor(
            GeminiChatModelFactory geminiChatModelFactory,
            ObjectMapper objectMapper) {
        this.geminiChatModelFactory = geminiChatModelFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExecutionBackend backend() {
        return ExecutionBackend.NATIVE;
    }

    @Override
    public boolean supports(CanonicalResourceRequest request, CatalogCandidateView candidate) {
        return GeminiGatewayResourceSupport.supportsGoogleGenAiCandidate(
                request,
                candidate,
                "/v1/images/generations"
        );
    }

    @Override
    public ResponseEntity<JsonNode> executeJson(
            GatewayResourceExecutionContext context,
            JsonNode requestBody,
            String defaultModel) {
        if (context.request().operation() != TranslationOperation.IMAGE_GENERATION) {
            throw new IllegalArgumentException("Gemini images executor 当前仅支持 /v1/images/generations。");
        }
        ObjectNode payload = requireObjectPayload(requestBody, defaultModel);
        String prompt = requiredText(payload, "prompt", "images.generation 请求缺少 prompt。");
        validateResponseFormat(payload.path("response_format").asText(null));
        GenerateImagesConfig.Builder configBuilder = GenerateImagesConfig.builder()
                .outputMimeType("image/png");
        Integer numberOfImages = parsePositiveInteger(payload.path("n").asText(null));
        if (numberOfImages != null) {
            configBuilder.numberOfImages(numberOfImages);
        }
        GenerateImagesResponse response = generateImages(context, prompt, configBuilder.build());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(toOpenAiResponse(context, response));
    }

    GenerateImagesResponse generateImages(
            GatewayResourceExecutionContext context,
            String prompt,
            GenerateImagesConfig config) {
        try (Client client = GeminiGatewayResourceSupport.createClient(geminiChatModelFactory, context)) {
            return client.models.generateImages(
                    context.selectionResult().resolvedModelKey(),
                    prompt,
                    config
            );
        }
    }

    private ObjectNode toOpenAiResponse(GatewayResourceExecutionContext context, GenerateImagesResponse response) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("created", Instant.now().getEpochSecond());
        body.put("model", GeminiGatewayResourceSupport.responseModel(context));
        ArrayNode data = body.putArray("data");
        for (GeneratedImage generatedImage : response.generatedImages().orElse(java.util.List.of())) {
            Image image = generatedImage.image().orElse(null);
            if (image == null || image.imageBytes().isEmpty()) {
                continue;
            }
            data.addObject()
                    .put("b64_json", Base64.getEncoder().encodeToString(image.imageBytes().get()));
        }
        if (data.isEmpty()) {
            throw new IllegalStateException("Gemini image generation 未返回可用图片。");
        }
        return body;
    }

    private void validateResponseFormat(String responseFormat) {
        String normalized = trimToNull(responseFormat);
        if (normalized == null || "b64_json".equalsIgnoreCase(normalized)) {
            return;
        }
        throw new IllegalArgumentException("Gemini image generation 当前仅返回 b64_json。");
    }

    private ObjectNode requireObjectPayload(JsonNode requestBody, String defaultModel) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new IllegalArgumentException("请求体必须是 JSON object。");
        }
        ObjectNode payload = (ObjectNode) requestBody;
        if (!payload.hasNonNull("model") && defaultModel != null && !defaultModel.isBlank()) {
            payload.put("model", defaultModel);
        }
        return payload;
    }

    private String requiredText(ObjectNode payload, String fieldName, String message) {
        String value = trimToNull(payload.path(fieldName).asText(null));
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private Integer parsePositiveInteger(String rawValue) {
        String value = trimToNull(rawValue);
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException("n 必须大于 0。");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("n 必须是整数。", exception);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
