package com.prodigalgal.xaigateway.gateway.core.interop;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlanCompilation;
import com.prodigalgal.xaigateway.admin.application.ErrorRuleService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanRequest;
import com.prodigalgal.xaigateway.protocol.ingress.interop.InteropPlanResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GatewayInteropPlanService {

    private final ErrorRuleService errorRuleService;
    private final TranslationExecutionPlanCompiler translationExecutionPlanCompiler;

    @Autowired
    public GatewayInteropPlanService(
            ErrorRuleService errorRuleService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler) {
        this.errorRuleService = errorRuleService;
        this.translationExecutionPlanCompiler = translationExecutionPlanCompiler;
    }

    public GatewayInteropPlanService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            ObjectMapper objectMapper,
            ErrorRuleService errorRuleService,
            SiteCapabilityTruthService siteCapabilityTruthService,
            GatewayRequestFeatureService gatewayRequestFeatureService) {
        this(
                errorRuleService,
                new TranslationExecutionPlanCompiler(
                        gatewayRouteSelectionService,
                        gatewayRequestFeatureService,
                        siteCapabilityTruthService
                )
        );
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
        CanonicalExecutionPlanCompilation compilation = translationExecutionPlanCompiler.compilePreview(
                distributedKeyPrefix,
                protocol,
                requestPath,
                request.requestedModel(),
                degradationPolicy,
                clientFamily,
                request.body()
        );
        CanonicalExecutionPlan executionPlan = compilation.canonicalPlan();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("executable", executionPlan.executable());
        summary.put("protocol", executionPlan.ingressProtocol().name().toLowerCase(Locale.ROOT));
        summary.put("requestPath", executionPlan.requestPath());
        summary.put("requestedModel", executionPlan.requestedModel());
        summary.put("resourceType", executionPlan.resourceType().wireName());
        summary.put("operation", executionPlan.operation().wireName());
        summary.put("degradationPolicy", degradationPolicy.name().toLowerCase(Locale.ROOT));
        summary.put("requiredFeatures", executionPlan.requiredFeatures().stream().map(InteropFeature::wireName).toList());
        summary.put("blockerCount", executionPlan.blockers().size());
        summary.put("degradationCount", executionPlan.degradations().size());
        summary.put("renderCapabilityLevel", executionPlan.renderCapabilityLevel() == null
                ? null
                : executionPlan.renderCapabilityLevel().name().toLowerCase(Locale.ROOT));

        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("featureLevels", executionPlan.featureLevels());
        debug.put("canonicalExecutionPlan", executionPlan);
        if (compilation.selectionResult() != null) {
            debug.put("distributedKeyId", compilation.selectionResult().distributedKeyId());
            debug.put("publicModel", compilation.selectionResult().publicModel());
            debug.put("resolvedModelKey", compilation.selectionResult().resolvedModelKey());
            debug.put("selectionSource", compilation.selectionResult().selectionSource());
            debug.put("candidateCount", compilation.selectionResult().candidates().size());
            debug.put("prefixHash", compilation.selectionResult().prefixHash());
            debug.put("fingerprint", compilation.selectionResult().fingerprint());
            debug.put("modelGroup", compilation.selectionResult().modelGroup());
            debug.put("clientFamily", compilation.selectionResult().clientFamily().name().toLowerCase(Locale.ROOT));
            debug.put("governanceNotes", compilation.selectionResult().governanceNotes());
            if (errorRuleService != null) {
                debug.put("potentialErrorRules", errorRuleService.potentialMatches(
                        compilation.selectionResult().selectedCandidate().candidate().providerType().name(),
                        executionPlan.ingressProtocol().name().toLowerCase(Locale.ROOT),
                        executionPlan.requestedModel(),
                        executionPlan.requestPath()
                ));
            }
        }

        return InteropPlanResponse.from(executionPlan, compilation.selectionResult(), summary, debug);
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

}
