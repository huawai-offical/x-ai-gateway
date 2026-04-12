package com.prodigalgal.xaigateway.gateway.core.interop;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class GatewayRequestFeatureService {

    public List<InteropFeature> detectRequiredFeatures(String requestPath, JsonNode body) {
        Set<InteropFeature> features = new LinkedHashSet<>();
        if ("/v1/chat/completions".equals(requestPath)) {
            features.add(InteropFeature.CHAT_TEXT);
            collectChatFeatures(features, body);
            return List.copyOf(features);
        }
        if ("/v1/responses".equals(requestPath)) {
            features.add(InteropFeature.RESPONSE_OBJECT);
            collectResponsesFeatures(features, body);
            return List.copyOf(features);
        }
        if ("/v1/embeddings".equals(requestPath)) {
            return List.of(InteropFeature.EMBEDDINGS);
        }
        if ("/v1/audio/transcriptions".equals(requestPath)) {
            return List.of(InteropFeature.AUDIO_TRANSCRIPTION);
        }
        if ("/v1/audio/translations".equals(requestPath)) {
            return List.of(InteropFeature.AUDIO_TRANSLATION);
        }
        if ("/v1/audio/speech".equals(requestPath)) {
            return List.of(InteropFeature.AUDIO_SPEECH);
        }
        if ("/v1/images/generations".equals(requestPath)) {
            return List.of(InteropFeature.IMAGE_GENERATION);
        }
        if ("/v1/images/edits".equals(requestPath)) {
            return List.of(InteropFeature.IMAGE_EDIT);
        }
        if ("/v1/images/variations".equals(requestPath)) {
            return List.of(InteropFeature.IMAGE_VARIATION);
        }
        if ("/v1/moderations".equals(requestPath)) {
            return List.of(InteropFeature.MODERATION);
        }
        if ("/v1/uploads".equals(requestPath)) {
            return List.of(InteropFeature.UPLOAD_CREATE);
        }
        if ("/v1/batches".equals(requestPath)) {
            return List.of(InteropFeature.BATCH_CREATE);
        }
        if ("/v1/fine_tuning/jobs".equals(requestPath)) {
            return List.of(InteropFeature.TUNING_CREATE);
        }
        if ("/v1/realtime/client_secrets".equals(requestPath)) {
            return List.of(InteropFeature.REALTIME_CLIENT_SECRET);
        }
        return List.of(InteropFeature.CHAT_TEXT);
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
}
