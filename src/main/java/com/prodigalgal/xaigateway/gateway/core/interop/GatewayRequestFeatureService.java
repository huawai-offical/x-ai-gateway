package com.prodigalgal.xaigateway.gateway.core.interop;

import tools.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class GatewayRequestFeatureService {

    public GatewayRequestSemantics describe(String requestPath, JsonNode body) {
        return describe("POST", requestPath, body);
    }

    public GatewayRequestSemantics describe(String httpMethod, String requestPath, JsonNode body) {
        String method = normalizeMethod(httpMethod);
        String normalizedPath = normalizePath(requestPath);
        Set<InteropFeature> features = new LinkedHashSet<>();
        if ("/v1/chat/completions".equals(normalizedPath)) {
            features.add(InteropFeature.CHAT_TEXT);
            collectChatFeatures(features, body);
            return new GatewayRequestSemantics(
                    TranslationResourceType.CHAT,
                    TranslationOperation.CHAT_COMPLETION,
                    List.copyOf(features),
                    true
            );
        }
        if ("/v1/responses".equals(normalizedPath)) {
            features.add(InteropFeature.RESPONSE_OBJECT);
            collectResponsesFeatures(features, body);
            return new GatewayRequestSemantics(
                    TranslationResourceType.RESPONSE,
                    TranslationOperation.RESPONSE_CREATE,
                    List.copyOf(features),
                    true
            );
        }
        if ("/v1/messages".equals(normalizedPath)) {
            features.add(InteropFeature.CHAT_TEXT);
            collectAnthropicFeatures(features, body);
            return new GatewayRequestSemantics(
                    TranslationResourceType.CHAT,
                    TranslationOperation.CHAT_COMPLETION,
                    List.copyOf(features),
                    true
            );
        }
        if (normalizedPath != null
                && normalizedPath.startsWith("/v1beta/models/")
                && (normalizedPath.contains(":generateContent") || normalizedPath.contains(":streamGenerateContent"))) {
            features.add(InteropFeature.CHAT_TEXT);
            collectGeminiFeatures(features, body);
            return new GatewayRequestSemantics(
                    TranslationResourceType.CHAT,
                    TranslationOperation.CHAT_COMPLETION,
                    List.copyOf(features),
                    true
            );
        }
        if ("POST".equals(method) && "/v1/embeddings".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.EMBEDDING,
                    TranslationOperation.EMBEDDING_CREATE,
                    List.of(InteropFeature.EMBEDDINGS),
                    true
            );
        }
        if ("POST".equals(method) && "/v1/files".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.FILE,
                    TranslationOperation.FILE_CREATE,
                    List.of(InteropFeature.FILE_OBJECT),
                    true
            );
        }
        if ("GET".equals(method) && "/v1/files".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.FILE,
                    TranslationOperation.FILE_LIST,
                    List.of(InteropFeature.FILE_OBJECT),
                    false
            );
        }
        if ("GET".equals(method) && "/v1/files/{fileId}".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.FILE,
                    TranslationOperation.FILE_GET,
                    List.of(InteropFeature.FILE_OBJECT),
                    false
            );
        }
        if ("GET".equals(method) && "/v1/files/{fileId}/content".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.FILE,
                    TranslationOperation.FILE_CONTENT_GET,
                    List.of(InteropFeature.FILE_OBJECT),
                    false
            );
        }
        if ("DELETE".equals(method) && "/v1/files/{fileId}".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.FILE,
                    TranslationOperation.FILE_DELETE,
                    List.of(InteropFeature.FILE_OBJECT),
                    true
            );
        }
        if ("POST".equals(method) && "/v1/audio/transcriptions".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.AUDIO,
                    TranslationOperation.AUDIO_TRANSCRIPTION,
                    List.of(InteropFeature.AUDIO_TRANSCRIPTION),
                    true
            );
        }
        if ("POST".equals(method) && "/v1/audio/translations".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.AUDIO,
                    TranslationOperation.AUDIO_TRANSLATION,
                    List.of(InteropFeature.AUDIO_TRANSLATION),
                    true
            );
        }
        if ("POST".equals(method) && "/v1/audio/speech".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.AUDIO,
                    TranslationOperation.AUDIO_SPEECH,
                    List.of(InteropFeature.AUDIO_SPEECH),
                    true
            );
        }
        if ("POST".equals(method) && "/v1/images/generations".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.IMAGE,
                    TranslationOperation.IMAGE_GENERATION,
                    List.of(InteropFeature.IMAGE_GENERATION),
                    true
            );
        }
        if ("POST".equals(method) && "/v1/images/edits".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.IMAGE,
                    TranslationOperation.IMAGE_EDIT,
                    List.of(InteropFeature.IMAGE_EDIT),
                    true
            );
        }
        if ("POST".equals(method) && "/v1/images/variations".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.IMAGE,
                    TranslationOperation.IMAGE_VARIATION,
                    List.of(InteropFeature.IMAGE_VARIATION),
                    true
            );
        }
        if ("POST".equals(method) && "/v1/moderations".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.MODERATION,
                    TranslationOperation.MODERATION_CREATE,
                    List.of(InteropFeature.MODERATION),
                    true
            );
        }
        if ("POST".equals(method) && "/v1/uploads".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.UPLOAD,
                    TranslationOperation.UPLOAD_CREATE,
                    List.of(InteropFeature.UPLOAD_CREATE, InteropFeature.FILE_OBJECT),
                    true
            );
        }
        if ("GET".equals(method) && "/v1/uploads/{uploadId}".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.UPLOAD,
                    TranslationOperation.UPLOAD_GET,
                    List.of(InteropFeature.UPLOAD_CREATE),
                    false
            );
        }
        if ("POST".equals(method) && "/v1/uploads/{uploadId}/parts".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.UPLOAD,
                    TranslationOperation.UPLOAD_PART_ADD,
                    List.of(InteropFeature.UPLOAD_CREATE, InteropFeature.FILE_OBJECT),
                    true
            );
        }
        if ("POST".equals(method) && "/v1/uploads/{uploadId}/complete".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.UPLOAD,
                    TranslationOperation.UPLOAD_COMPLETE,
                    List.of(InteropFeature.UPLOAD_CREATE),
                    false
            );
        }
        if ("POST".equals(method) && "/v1/uploads/{uploadId}/cancel".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.UPLOAD,
                    TranslationOperation.UPLOAD_CANCEL,
                    List.of(InteropFeature.UPLOAD_CREATE),
                    false
            );
        }
        if ("POST".equals(method) && "/v1/batches".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.BATCH,
                    TranslationOperation.BATCH_CREATE,
                    List.of(InteropFeature.BATCH_CREATE, InteropFeature.FILE_OBJECT),
                    true
            );
        }
        if ("GET".equals(method) && "/v1/batches/{batchId}".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.BATCH,
                    TranslationOperation.BATCH_GET,
                    List.of(InteropFeature.BATCH_CREATE),
                    false
            );
        }
        if ("POST".equals(method) && "/v1/batches/{batchId}/cancel".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.BATCH,
                    TranslationOperation.BATCH_CANCEL,
                    List.of(InteropFeature.BATCH_CREATE),
                    false
            );
        }
        if ("POST".equals(method) && "/v1/fine_tuning/jobs".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.TUNING,
                    TranslationOperation.TUNING_CREATE,
                    List.of(InteropFeature.TUNING_CREATE, InteropFeature.FILE_OBJECT),
                    true
            );
        }
        if ("GET".equals(method) && "/v1/fine_tuning/jobs/{jobId}".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.TUNING,
                    TranslationOperation.TUNING_GET,
                    List.of(InteropFeature.TUNING_CREATE),
                    false
            );
        }
        if ("POST".equals(method) && "/v1/fine_tuning/jobs/{jobId}/cancel".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.TUNING,
                    TranslationOperation.TUNING_CANCEL,
                    List.of(InteropFeature.TUNING_CREATE),
                    false
            );
        }
        if ("POST".equals(method) && "/v1/realtime/client_secrets".equals(normalizedPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.REALTIME,
                    TranslationOperation.REALTIME_CLIENT_SECRET_CREATE,
                    List.of(InteropFeature.REALTIME_CLIENT_SECRET),
                    false
            );
        }
        return new GatewayRequestSemantics(
                TranslationResourceType.UNKNOWN,
                TranslationOperation.UNKNOWN,
                List.of(InteropFeature.CHAT_TEXT),
                true
        );
    }

    public String normalizePath(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return requestPath;
        }
        if (requestPath.matches("^/v1/files/[^/]+/content$")) {
            return "/v1/files/{fileId}/content";
        }
        if (requestPath.matches("^/v1/files/[^/]+$")) {
            return "/v1/files/{fileId}";
        }
        if (requestPath.matches("^/v1/uploads/[^/]+/parts$")) {
            return "/v1/uploads/{uploadId}/parts";
        }
        if (requestPath.matches("^/v1/uploads/[^/]+/complete$")) {
            return "/v1/uploads/{uploadId}/complete";
        }
        if (requestPath.matches("^/v1/uploads/[^/]+/cancel$")) {
            return "/v1/uploads/{uploadId}/cancel";
        }
        if (requestPath.matches("^/v1/uploads/[^/]+$")) {
            return "/v1/uploads/{uploadId}";
        }
        if (requestPath.matches("^/v1/batches/[^/]+/cancel$")) {
            return "/v1/batches/{batchId}/cancel";
        }
        if (requestPath.matches("^/v1/batches/[^/]+$")) {
            return "/v1/batches/{batchId}";
        }
        if (requestPath.matches("^/v1/fine_tuning/jobs/[^/]+/cancel$")) {
            return "/v1/fine_tuning/jobs/{jobId}/cancel";
        }
        if (requestPath.matches("^/v1/fine_tuning/jobs/[^/]+$")) {
            return "/v1/fine_tuning/jobs/{jobId}";
        }
        return requestPath;
    }

    public java.util.Map<String, String> extractPathParams(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return java.util.Map.of();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^/v1/files/([^/]+)/content$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("fileId", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1/files/([^/]+)$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("fileId", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1/uploads/([^/]+)/parts$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("uploadId", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1/uploads/([^/]+)/(complete|cancel)$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("uploadId", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1/uploads/([^/]+)$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("uploadId", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1/batches/([^/]+)(?:/cancel)?$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("batchId", matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("^/v1/fine_tuning/jobs/([^/]+)(?:/cancel)?$").matcher(requestPath);
        if (matcher.matches()) {
            return java.util.Map.of("jobId", matcher.group(1));
        }
        return java.util.Map.of();
    }

    private String normalizeMethod(String httpMethod) {
        if (httpMethod == null || httpMethod.isBlank()) {
            return "POST";
        }
        return httpMethod.trim().toUpperCase();
    }

    public List<InteropFeature> detectRequiredFeatures(String requestPath, JsonNode body) {
        return describe(requestPath, body).requiredFeatures();
    }

    private void collectChatFeatures(Set<InteropFeature> features, JsonNode body) {
        if (body == null || !body.isObject()) {
            return;
        }
        if (body.has("tools") && body.get("tools").isArray() && !body.get("tools").isEmpty()) {
            features.add(InteropFeature.TOOLS);
        }
        if (body.has("reasoning") || body.has("reasoning_effort")) {
            features.add(InteropFeature.REASONING);
        }
        JsonNode messages = body.path("messages");
        if (!messages.isArray()) {
            return;
        }
        for (JsonNode message : messages) {
            JsonNode content = message.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode item : content) {
                String type = item.path("type").asText("");
                if ("image_url".equalsIgnoreCase(type) || "input_image".equalsIgnoreCase(type)) {
                    features.add(InteropFeature.IMAGE_INPUT);
                }
                if ("input_file".equalsIgnoreCase(type)) {
                    features.add(InteropFeature.FILE_INPUT);
                }
            }
        }
    }

    private void collectResponsesFeatures(Set<InteropFeature> features, JsonNode body) {
        if (body == null || !body.isObject()) {
            return;
        }
        if (body.has("tools") && body.get("tools").isArray() && !body.get("tools").isEmpty()) {
            features.add(InteropFeature.TOOLS);
        }
        if (body.has("reasoning") || body.has("reasoning_effort")) {
            features.add(InteropFeature.REASONING);
        }
        JsonNode input = body.path("input");
        if (!input.isArray()) {
            return;
        }
        for (JsonNode item : input) {
            String type = item.path("type").asText("");
            if ("input_image".equalsIgnoreCase(type) || "image_url".equalsIgnoreCase(type)) {
                features.add(InteropFeature.IMAGE_INPUT);
            }
            if ("input_file".equalsIgnoreCase(type)) {
                features.add(InteropFeature.FILE_INPUT);
            }
        }
    }

    private void collectAnthropicFeatures(Set<InteropFeature> features, JsonNode body) {
        if (body == null || !body.isObject()) {
            return;
        }
        if (body.has("tools") && body.get("tools").isArray() && !body.get("tools").isEmpty()) {
            features.add(InteropFeature.TOOLS);
        }
        if (body.has("thinking")) {
            features.add(InteropFeature.REASONING);
        }
        JsonNode messages = body.path("messages");
        if (!messages.isArray()) {
            return;
        }
        for (JsonNode message : messages) {
            JsonNode content = message.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode item : content) {
                String type = item.path("type").asText("");
                if ("image".equalsIgnoreCase(type)) {
                    features.add(InteropFeature.IMAGE_INPUT);
                }
                if ("document".equalsIgnoreCase(type)) {
                    features.add(InteropFeature.FILE_INPUT);
                }
            }
        }
    }

    private void collectGeminiFeatures(Set<InteropFeature> features, JsonNode body) {
        if (body == null || !body.isObject()) {
            return;
        }
        if (body.has("tools") && body.get("tools").isArray() && !body.get("tools").isEmpty()) {
            features.add(InteropFeature.TOOLS);
        }
        JsonNode generationConfig = body.path("generationConfig");
        if (generationConfig.isObject() && (generationConfig.has("thinkingConfig")
                || generationConfig.has("thinkingBudget")
                || generationConfig.has("thinkingLevel"))) {
            features.add(InteropFeature.REASONING);
        }
        JsonNode contents = body.path("contents");
        if (!contents.isArray()) {
            return;
        }
        for (JsonNode content : contents) {
            JsonNode parts = content.path("parts");
            if (!parts.isArray()) {
                continue;
            }
            for (JsonNode part : parts) {
                JsonNode fileData = part.path("fileData");
                if (fileData.isObject()) {
                    String mimeType = fileData.path("mimeType").asText("");
                    if (mimeType.startsWith("image/")) {
                        features.add(InteropFeature.IMAGE_INPUT);
                    } else {
                        features.add(InteropFeature.FILE_INPUT);
                    }
                }
            }
        }
    }
}
