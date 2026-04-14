package com.prodigalgal.xaigateway.gateway.core.interop;

import tools.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class GatewayRequestFeatureService {

    public GatewayRequestSemantics describe(String requestPath, JsonNode body) {
        Set<InteropFeature> features = new LinkedHashSet<>();
        if ("/v1/chat/completions".equals(requestPath)) {
            features.add(InteropFeature.CHAT_TEXT);
            collectChatFeatures(features, body);
            return new GatewayRequestSemantics(
                    TranslationResourceType.CHAT,
                    TranslationOperation.CHAT_COMPLETION,
                    List.copyOf(features),
                    true
            );
        }
        if ("/v1/responses".equals(requestPath)) {
            features.add(InteropFeature.RESPONSE_OBJECT);
            collectResponsesFeatures(features, body);
            return new GatewayRequestSemantics(
                    TranslationResourceType.RESPONSE,
                    TranslationOperation.RESPONSE_CREATE,
                    List.copyOf(features),
                    true
            );
        }
        if ("/v1/messages".equals(requestPath)) {
            features.add(InteropFeature.CHAT_TEXT);
            collectAnthropicFeatures(features, body);
            return new GatewayRequestSemantics(
                    TranslationResourceType.CHAT,
                    TranslationOperation.CHAT_COMPLETION,
                    List.copyOf(features),
                    true
            );
        }
        if (requestPath != null
                && requestPath.startsWith("/v1beta/models/")
                && (requestPath.contains(":generateContent") || requestPath.contains(":streamGenerateContent"))) {
            features.add(InteropFeature.CHAT_TEXT);
            collectGeminiFeatures(features, body);
            return new GatewayRequestSemantics(
                    TranslationResourceType.CHAT,
                    TranslationOperation.CHAT_COMPLETION,
                    List.copyOf(features),
                    true
            );
        }
        if ("/v1/embeddings".equals(requestPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.EMBEDDING,
                    TranslationOperation.EMBEDDING_CREATE,
                    List.of(InteropFeature.EMBEDDINGS),
                    true
            );
        }
        if ("/v1/files".equals(requestPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.FILE,
                    TranslationOperation.FILE_CREATE,
                    List.of(InteropFeature.FILE_OBJECT),
                    false
            );
        }
        if ("/v1/audio/transcriptions".equals(requestPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.AUDIO,
                    TranslationOperation.AUDIO_TRANSCRIPTION,
                    List.of(InteropFeature.AUDIO_TRANSCRIPTION),
                    true
            );
        }
        if ("/v1/audio/translations".equals(requestPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.AUDIO,
                    TranslationOperation.AUDIO_TRANSLATION,
                    List.of(InteropFeature.AUDIO_TRANSLATION),
                    true
            );
        }
        if ("/v1/audio/speech".equals(requestPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.AUDIO,
                    TranslationOperation.AUDIO_SPEECH,
                    List.of(InteropFeature.AUDIO_SPEECH),
                    true
            );
        }
        if ("/v1/images/generations".equals(requestPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.IMAGE,
                    TranslationOperation.IMAGE_GENERATION,
                    List.of(InteropFeature.IMAGE_GENERATION),
                    true
            );
        }
        if ("/v1/images/edits".equals(requestPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.IMAGE,
                    TranslationOperation.IMAGE_EDIT,
                    List.of(InteropFeature.IMAGE_EDIT),
                    true
            );
        }
        if ("/v1/images/variations".equals(requestPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.IMAGE,
                    TranslationOperation.IMAGE_VARIATION,
                    List.of(InteropFeature.IMAGE_VARIATION),
                    true
            );
        }
        if ("/v1/moderations".equals(requestPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.MODERATION,
                    TranslationOperation.MODERATION_CREATE,
                    List.of(InteropFeature.MODERATION),
                    true
            );
        }
        if ("/v1/uploads".equals(requestPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.UPLOAD,
                    TranslationOperation.UPLOAD_CREATE,
                    List.of(InteropFeature.UPLOAD_CREATE),
                    false
            );
        }
        if (requestPath != null && requestPath.matches("^/v1/uploads/[^/]+/parts$")) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.UPLOAD,
                    TranslationOperation.UPLOAD_PART_ADD,
                    List.of(InteropFeature.UPLOAD_CREATE),
                    false
            );
        }
        if (requestPath != null && requestPath.matches("^/v1/uploads/[^/]+/complete$")) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.UPLOAD,
                    TranslationOperation.UPLOAD_COMPLETE,
                    List.of(InteropFeature.UPLOAD_CREATE),
                    false
            );
        }
        if (requestPath != null && requestPath.matches("^/v1/uploads/[^/]+/cancel$")) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.UPLOAD,
                    TranslationOperation.UPLOAD_CANCEL,
                    List.of(InteropFeature.UPLOAD_CREATE),
                    false
            );
        }
        if ("/v1/batches".equals(requestPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.BATCH,
                    TranslationOperation.BATCH_CREATE,
                    List.of(InteropFeature.BATCH_CREATE),
                    false
            );
        }
        if (requestPath != null && requestPath.matches("^/v1/batches/[^/]+/cancel$")) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.BATCH,
                    TranslationOperation.BATCH_CANCEL,
                    List.of(InteropFeature.BATCH_CREATE),
                    false
            );
        }
        if ("/v1/fine_tuning/jobs".equals(requestPath)) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.TUNING,
                    TranslationOperation.TUNING_CREATE,
                    List.of(InteropFeature.TUNING_CREATE),
                    false
            );
        }
        if (requestPath != null && requestPath.matches("^/v1/fine_tuning/jobs/[^/]+/cancel$")) {
            return new GatewayRequestSemantics(
                    TranslationResourceType.TUNING,
                    TranslationOperation.TUNING_CANCEL,
                    List.of(InteropFeature.TUNING_CREATE),
                    false
            );
        }
        if ("/v1/realtime/client_secrets".equals(requestPath)) {
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
