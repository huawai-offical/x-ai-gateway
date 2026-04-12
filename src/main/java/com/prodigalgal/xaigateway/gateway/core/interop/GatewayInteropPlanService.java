package com.prodigalgal.xaigateway.gateway.core.interop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.admin.application.ErrorRuleService;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanRequest;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GatewayInteropPlanService {

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final ObjectMapper objectMapper;
    private final ErrorRuleService errorRuleService;
    private final SiteCapabilityTruthService siteCapabilityTruthService;
    private final GatewayRequestFeatureService gatewayRequestFeatureService;

    @Autowired
    public GatewayInteropPlanService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            ObjectMapper objectMapper,
            ErrorRuleService errorRuleService,
            SiteCapabilityTruthService siteCapabilityTruthService,
            GatewayRequestFeatureService gatewayRequestFeatureService) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.objectMapper = objectMapper;
        this.errorRuleService = errorRuleService;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
        this.gatewayRequestFeatureService = gatewayRequestFeatureService;
    }

    public GatewayInteropPlanService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            ObjectMapper objectMapper) {
        this(gatewayRouteSelectionService, objectMapper, null, null, null);
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
        List<InteropFeature> requiredFeatures = gatewayRequestFeatureService == null
                ? List.of(InteropFeature.CHAT_TEXT)
                : gatewayRequestFeatureService.detectRequiredFeatures(requestPath, body);

        RouteSelectionResult selectionResult = null;
        List<String> blockers = new ArrayList<>();
        List<String> degradations = new ArrayList<>();
        Map<String, String> featureLevels = new LinkedHashMap<>();
        ProviderType selectedProviderType = null;

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
            selectedProviderType = selectionResult.selectedCandidate().candidate().providerType();
            for (InteropFeature feature : requiredFeatures) {
                InteropCapabilityLevel level = siteCapabilityTruthService == null
                        ? capabilityLevel(selectedProviderType, feature)
                        : siteCapabilityTruthService.capabilityLevel(selectionResult.selectedCandidate().candidate(), feature);
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
        TranslationExecutionPlan executionPlan = buildExecutionPlan(selectionResult, degradations, blockers, featureLevels);
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
            if (errorRuleService != null) {
                debug.put("potentialErrorRules", errorRuleService.potentialMatches(
                        selectedProviderType.name(),
                        protocol,
                        requestedModel,
                        requestPath
                ));
            }
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
                executionPlan.providerFamily(),
                executionPlan.siteProfileId(),
                executionPlan.executionKind(),
                executionPlan.capabilityLevel() == null ? null : executionPlan.capabilityLevel().name().toLowerCase(Locale.ROOT),
                executionPlan.authStrategy(),
                executionPlan.pathStrategy(),
                executionPlan.errorSchemaStrategy(),
                selectionResult,
                summary,
                debug
        );
    }

    private TranslationExecutionPlan buildExecutionPlan(
            RouteSelectionResult selectionResult,
            List<String> degradations,
            List<String> blockers,
            Map<String, String> featureLevels) {
        if (selectionResult == null) {
            return new TranslationExecutionPlan(
                    false,
                    null,
                    null,
                    ExecutionKind.BLOCKED,
                    InteropCapabilityLevel.UNSUPPORTED,
                    List.of(),
                    List.copyOf(blockers),
                    null,
                    null,
                    null,
                    Map.of(),
                    Map.of()
            );
        }

        var candidate = selectionResult.selectedCandidate().candidate();
        InteropCapabilityLevel capabilityLevel = featureLevels.containsValue("unsupported")
                ? InteropCapabilityLevel.UNSUPPORTED
                : featureLevels.containsValue("lossy")
                ? InteropCapabilityLevel.LOSSY
                : featureLevels.containsValue("emulated")
                ? InteropCapabilityLevel.EMULATED
                : InteropCapabilityLevel.NATIVE;
        ExecutionKind executionKind = !blockers.isEmpty()
                ? ExecutionKind.BLOCKED
                : capabilityLevel == InteropCapabilityLevel.NATIVE && isNativeProtocol(selectionResult.protocol(), candidate.providerType())
                ? ExecutionKind.NATIVE
                : capabilityLevel == InteropCapabilityLevel.EMULATED
                ? ExecutionKind.EMULATED
                : ExecutionKind.TRANSLATED;

        return new TranslationExecutionPlan(
                blockers.isEmpty(),
                candidate.providerFamily(),
                candidate.siteProfileId(),
                executionKind,
                capabilityLevel,
                List.copyOf(degradations),
                List.copyOf(blockers),
                candidate.authStrategy(),
                candidate.pathStrategy(),
                candidate.errorSchemaStrategy(),
                Map.of(
                        "protocol", selectionResult.protocol(),
                        "requestedModel", selectionResult.requestedModel(),
                        "resolvedModelKey", selectionResult.resolvedModelKey()
                ),
                Map.of(
                        "publicModel", selectionResult.publicModel(),
                        "selectionSource", selectionResult.selectionSource().name()
                )
        );
    }

    private boolean isNativeProtocol(String protocol, ProviderType providerType) {
        return switch (protocol == null ? "" : protocol.toLowerCase(Locale.ROOT)) {
            case "anthropic_native" -> providerType == ProviderType.ANTHROPIC_DIRECT;
            case "google_native" -> providerType == ProviderType.GEMINI_DIRECT;
            default -> providerType == ProviderType.OPENAI_DIRECT || providerType == ProviderType.OPENAI_COMPATIBLE;
        };
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
