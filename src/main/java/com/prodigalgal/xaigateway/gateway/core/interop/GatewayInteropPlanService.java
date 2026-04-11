package com.prodigalgal.xaigateway.gateway.core.interop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanRequest;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class GatewayInteropPlanService {

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final ObjectMapper objectMapper;

    public GatewayInteropPlanService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            ObjectMapper objectMapper) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.objectMapper = objectMapper;
    }

    public InteropPlanResponse preview(String distributedKeyPrefix, InteropPlanRequest request) {
        String protocol = normalizeProtocol(request.protocol());
        String requestPath = normalizeRequestPath(request.requestPath(), protocol);
        GatewayDegradationPolicy degradationPolicy = GatewayDegradationPolicy.from(request.degradationPolicy());
        GatewayClientFamily clientFamily = request.clientFamily() == null
                ? GatewayClientFamily.GENERIC_OPENAI
                : GatewayClientFamily.from(request.clientFamily());
        JsonNode body = request.body();
        String requestedModel = resolveRequestedModel(request.requestedModel(), requestPath, body);
        List<InteropFeature> requiredFeatures = detectRequiredFeatures(requestPath, body);

        RouteSelectionResult selectionResult = null;
        List<String> blockers = new ArrayList<>();
        List<String> degradations = new ArrayList<>();
        Map<String, String> featureLevels = new LinkedHashMap<>();

        try {
            selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                distributedKeyPrefix,
                protocol,
                requestPath,
                requestedModel,
                body,
                clientFamily,
                false
        ));
        } catch (IllegalArgumentException exception) {
            blockers.add(exception.getMessage());
        }

        if (selectionResult != null) {
            ProviderType providerType = selectionResult.selectedCandidate().candidate().providerType();
            for (InteropFeature feature : requiredFeatures) {
                InteropCapabilityLevel level = capabilityLevel(providerType, feature);
                featureLevels.put(feature.wireName(), level.name().toLowerCase(Locale.ROOT));
                if (level == InteropCapabilityLevel.UNSUPPORTED) {
                    blockers.add(feature.wireName() + " 当前 provider 不支持。");
                    continue;
                }
                if (!degradationPolicy.allows(level)) {
                    blockers.add(feature.wireName() + " 需要 " + level.name().toLowerCase(Locale.ROOT) + "，当前策略不允许。");
                    continue;
                }
                if (level != InteropCapabilityLevel.NATIVE) {
                    degradations.add(feature.wireName() + " 以 " + level.name().toLowerCase(Locale.ROOT) + " 执行。");
                }
            }
        }

        boolean executable = selectionResult != null && blockers.isEmpty();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("executable", executable);
        summary.put("protocol", protocol);
        summary.put("requestPath", requestPath);
        summary.put("requestedModel", requestedModel);
        summary.put("degradationPolicy", degradationPolicy.name().toLowerCase(Locale.ROOT));
        summary.put("requiredFeatures", requiredFeatures.stream().map(InteropFeature::wireName).toList());
        summary.put("blockerCount", blockers.size());
        summary.put("degradationCount", degradations.size());

        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("featureLevels", featureLevels);
        if (selectionResult != null) {
            debug.put("distributedKeyId", selectionResult.distributedKeyId());
            debug.put("publicModel", selectionResult.publicModel());
            debug.put("resolvedModelKey", selectionResult.resolvedModelKey());
            debug.put("selectionSource", selectionResult.selectionSource());
            debug.put("candidateCount", selectionResult.candidates().size());
            debug.put("prefixHash", selectionResult.prefixHash());
            debug.put("fingerprint", selectionResult.fingerprint());
            debug.put("modelGroup", selectionResult.modelGroup());
            debug.put("clientFamily", selectionResult.clientFamily().name().toLowerCase(Locale.ROOT));
            debug.put("governanceNotes", selectionResult.governanceNotes());
        }

        return new InteropPlanResponse(
                executable,
                protocol,
                requestPath,
                requestedModel,
                degradationPolicy.name().toLowerCase(Locale.ROOT),
                requiredFeatures.stream().map(InteropFeature::wireName).toList(),
                blockers,
                degradations,
                selectionResult,
                summary,
                debug
        );
    }

    private String normalizeProtocol(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            return "openai";
        }
        return protocol.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequestPath(String requestPath, String protocol) {
        if (requestPath != null && !requestPath.isBlank()) {
            return requestPath.trim();
        }
        return switch (protocol) {
            case "responses" -> "/v1/responses";
            case "embeddings" -> "/v1/embeddings";
            default -> "/v1/chat/completions";
        };
    }

    private String resolveRequestedModel(String requestedModel, String requestPath, JsonNode body) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return requestedModel.trim();
        }
        if (body != null && body.isObject()) {
            String model = body.path("model").asText(null);
            if (model != null && !model.isBlank()) {
                return model.trim();
            }
        }
        if (requestPath.startsWith("/v1/images")) {
            return "gpt-image-1";
        }
        if (requestPath.startsWith("/v1/moderations")) {
            return "omni-moderation-latest";
        }
        if (requestPath.startsWith("/v1/audio/speech")) {
            return "gpt-4o-mini-tts";
        }
        throw new IllegalArgumentException("预检请求缺少 model。");
    }

    private List<InteropFeature> detectRequiredFeatures(String requestPath, JsonNode body) {
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

    private InteropCapabilityLevel capabilityLevel(ProviderType providerType, InteropFeature feature) {
        return switch (providerType) {
            case OPENAI_DIRECT -> openAiDirectCapability(feature);
            case OPENAI_COMPATIBLE -> openAiCompatibleCapability(feature);
            case ANTHROPIC_DIRECT -> anthropicCapability(feature);
            case GEMINI_DIRECT -> geminiCapability(feature);
            case OLLAMA_DIRECT -> ollamaCapability(feature);
        };
    }

    private InteropCapabilityLevel openAiDirectCapability(InteropFeature feature) {
        return switch (feature) {
            case CHAT_TEXT, TOOLS, IMAGE_INPUT, FILE_INPUT, REASONING, RESPONSE_OBJECT, EMBEDDINGS,
                    AUDIO_TRANSCRIPTION, AUDIO_TRANSLATION, AUDIO_SPEECH, IMAGE_GENERATION, IMAGE_EDIT,
                    IMAGE_VARIATION, MODERATION -> InteropCapabilityLevel.NATIVE;
            case UPLOAD_CREATE, BATCH_CREATE, TUNING_CREATE, REALTIME_CLIENT_SECRET -> InteropCapabilityLevel.EMULATED;
        };
    }

    private InteropCapabilityLevel openAiCompatibleCapability(InteropFeature feature) {
        return switch (feature) {
            case CHAT_TEXT, TOOLS, IMAGE_INPUT, FILE_INPUT, EMBEDDINGS,
                    AUDIO_TRANSCRIPTION, AUDIO_TRANSLATION, AUDIO_SPEECH,
                    IMAGE_GENERATION, IMAGE_EDIT, IMAGE_VARIATION, MODERATION -> InteropCapabilityLevel.NATIVE;
            case RESPONSE_OBJECT, REASONING, UPLOAD_CREATE, BATCH_CREATE, TUNING_CREATE, REALTIME_CLIENT_SECRET ->
                    InteropCapabilityLevel.EMULATED;
        };
    }

    private InteropCapabilityLevel anthropicCapability(InteropFeature feature) {
        return switch (feature) {
            case CHAT_TEXT, TOOLS, IMAGE_INPUT, REASONING -> InteropCapabilityLevel.NATIVE;
            case RESPONSE_OBJECT, FILE_INPUT -> InteropCapabilityLevel.EMULATED;
            default -> InteropCapabilityLevel.UNSUPPORTED;
        };
    }

    private InteropCapabilityLevel geminiCapability(InteropFeature feature) {
        return switch (feature) {
            case CHAT_TEXT, TOOLS, IMAGE_INPUT, FILE_INPUT, REASONING, EMBEDDINGS -> InteropCapabilityLevel.NATIVE;
            case RESPONSE_OBJECT, TUNING_CREATE, REALTIME_CLIENT_SECRET -> InteropCapabilityLevel.EMULATED;
            default -> InteropCapabilityLevel.UNSUPPORTED;
        };
    }

    private InteropCapabilityLevel ollamaCapability(InteropFeature feature) {
        return switch (feature) {
            case CHAT_TEXT -> InteropCapabilityLevel.NATIVE;
            case RESPONSE_OBJECT, FILE_INPUT -> InteropCapabilityLevel.EMULATED;
            default -> InteropCapabilityLevel.UNSUPPORTED;
        };
    }
}
