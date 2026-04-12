package com.prodigalgal.xaigateway.gateway.core.interop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.application.ErrorRuleService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanRequest;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            for (InteropFeature feature : requiredFeatures) {
                InteropCapabilityLevel level = siteCapabilityTruthService.capabilityLevel(
                        selectionResult.selectedCandidate().candidate(),
                        feature
                );
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

        TranslationExecutionPlan executionPlan = siteCapabilityTruthService.buildExecutionPlan(
                selectionResult,
                requestPath,
                requiredFeatures,
                degradations,
                blockers
        );

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("executable", executionPlan.executable());
        summary.put("protocol", protocol);
        summary.put("requestPath", requestPath);
        summary.put("requestedModel", requestedModel);
        summary.put("resourceType", executionPlan.resourceType());
        summary.put("operation", executionPlan.operation());
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
                        selectionResult.selectedCandidate().candidate().providerType().name(),
                        protocol,
                        requestedModel,
                        requestPath
                ));
            }
        }

        return new InteropPlanResponse(
                executionPlan.executable(),
                protocol,
                requestPath,
                requestedModel,
                degradationPolicy.name().toLowerCase(Locale.ROOT),
                requiredFeatures.stream().map(InteropFeature::wireName).toList(),
                blockers,
                degradations,
                executionPlan.resourceType(),
                executionPlan.operation(),
                executionPlan.providerFamily(),
                executionPlan.siteProfileId(),
                executionPlan.executionKind(),
                executionPlan.capabilityLevel() == null
                        ? null
                        : executionPlan.capabilityLevel().name().toLowerCase(Locale.ROOT),
                executionPlan.upstreamObjectMode(),
                executionPlan.authStrategy(),
                executionPlan.pathStrategy(),
                executionPlan.errorSchemaStrategy(),
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
        if (requestPath.startsWith("/v1/files")
                || requestPath.startsWith("/v1/uploads")
                || requestPath.startsWith("/v1/batches")) {
            return "resource-orchestration";
        }
        throw new IllegalArgumentException("预检请求缺少 model。");
    }
}
