package com.prodigalgal.xaigateway.gateway.core.interop;

import tools.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlanCompilation;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRenderCapabilitySupport;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendDecision;
import com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendPolicyService;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TranslationExecutionPlanCompiler {

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final GatewayRequestFeatureService gatewayRequestFeatureService;
    private final SiteCapabilityTruthService siteCapabilityTruthService;
    private final ExecutionBackendPolicyService executionBackendPolicyService;

    @Autowired
    public TranslationExecutionPlanCompiler(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            SiteCapabilityTruthService siteCapabilityTruthService,
            ExecutionBackendPolicyService executionBackendPolicyService) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.gatewayRequestFeatureService = gatewayRequestFeatureService;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
        this.executionBackendPolicyService = executionBackendPolicyService;
    }

    public TranslationExecutionPlanCompiler(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            SiteCapabilityTruthService siteCapabilityTruthService) {
        this(
                gatewayRouteSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService,
                new ExecutionBackendPolicyService()
        );
    }

    public CanonicalExecutionPlanCompilation compilePreview(
            String distributedKeyPrefix,
            String protocol,
            String requestPath,
            String requestedModel,
            GatewayDegradationPolicy degradationPolicy,
            GatewayClientFamily clientFamily,
            JsonNode body) {
        return compilePreview(
                distributedKeyPrefix,
                protocol,
                "POST",
                requestPath,
                requestedModel,
                degradationPolicy,
                clientFamily,
                body
        );
    }

    public CanonicalExecutionPlanCompilation compilePreview(
            String distributedKeyPrefix,
            String protocol,
            String httpMethod,
            String requestPath,
            String requestedModel,
            GatewayDegradationPolicy degradationPolicy,
            GatewayClientFamily clientFamily,
            JsonNode body) {
        String normalizedProtocol = normalizeProtocol(protocol);
        String normalizedRequestPath = normalizeRequestPath(requestPath, normalizedProtocol);
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(httpMethod, normalizedRequestPath, body);
        String resolvedRequestedModel = resolveRequestedModel(requestedModel, normalizedRequestPath, semantics, body);
        List<String> blockedReasons = new ArrayList<>();
        List<String> lossReasons = new ArrayList<>();

        RouteSelectionResult selectionResult = null;
        if (semantics.requiresRouteSelection()) {
            try {
                selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                        distributedKeyPrefix,
                        normalizedProtocol,
                        normalizedRequestPath,
                        resolvedRequestedModel,
                        body,
                        clientFamily == null ? GatewayClientFamily.GENERIC_OPENAI : clientFamily,
                        false
                ));
            } catch (IllegalArgumentException exception) {
                blockedReasons.add(exception.getMessage());
            }
        }

        CapabilityResolutionReport report = selectionResult == null
                ? new CapabilityResolutionReport(
                        java.util.Map.of(),
                        semantics.requiresRouteSelection() ? InteropCapabilityLevel.UNSUPPORTED : InteropCapabilityLevel.NATIVE,
                        semantics.requiresRouteSelection() ? InteropCapabilityLevel.UNSUPPORTED : InteropCapabilityLevel.NATIVE,
                        semantics.requiresRouteSelection() ? InteropCapabilityLevel.UNSUPPORTED : InteropCapabilityLevel.NATIVE,
                        semantics.requiresRouteSelection() ? ExecutionKind.BLOCKED : ExecutionKind.NATIVE,
                        semantics.requiresRouteSelection() ? "blocked" : "resource-orchestration",
                        List.of(),
                        List.of()
                )
                : siteCapabilityTruthService.resolve(selectionResult.selectedCandidate().candidate(), semantics);

        if (selectionResult != null) {
            blockedReasons.addAll(report.blockedReasons());
            lossReasons.addAll(report.lossReasons());
        }
        if (selectionResult != null && degradationPolicy != null && !degradationPolicy.allows(report.overallEffectiveLevel())) {
            blockedReasons.add("当前策略不允许 " + report.overallEffectiveLevel().name().toLowerCase(Locale.ROOT) + " 执行。");
        }

        CanonicalRequest canonicalRequest = new CanonicalRequest(
                distributedKeyPrefix,
                CanonicalIngressProtocol.from(normalizedProtocol),
                normalizedRequestPath,
                resolvedRequestedModel,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                body
        );
        CanonicalExecutionPlan plan = buildPlan(
                normalizedProtocol,
                normalizedRequestPath,
                resolvedRequestedModel,
                clientFamily == null ? GatewayClientFamily.GENERIC_OPENAI : clientFamily,
                semantics,
                selectionResult,
                report,
                blockedReasons.isEmpty()
                        ? report.executionKind()
                        : ExecutionKind.BLOCKED,
                blockedReasons.isEmpty()
                        ? report.upstreamObjectMode()
                        : "blocked",
                canonicalRequest,
                body,
                List.copyOf(lossReasons),
                List.copyOf(blockedReasons)
        );
        return new CanonicalExecutionPlanCompilation(plan, selectionResult, semantics, canonicalRequest);
    }

    public CanonicalExecutionPlanCompilation compileSelected(
            RouteSelectionResult selectionResult,
            CanonicalRequest canonicalRequest,
            GatewayRequestSemantics semantics,
            JsonNode body) {
        CapabilityResolutionReport report = siteCapabilityTruthService.resolve(selectionResult.selectedCandidate().candidate(), semantics);
        CanonicalExecutionPlan plan = buildPlan(
                selectionResult.protocol(),
                canonicalRequest.requestPath(),
                selectionResult.requestedModel(),
                selectionResult.clientFamily(),
                semantics,
                selectionResult,
                report,
                report.executionKind(),
                report.upstreamObjectMode(),
                canonicalRequest,
                body,
                report.lossReasons(),
                report.blockedReasons()
        );
        return new CanonicalExecutionPlanCompilation(plan, selectionResult, semantics, canonicalRequest);
    }

    public CanonicalExecutionPlanCompilation compileSelected(
            RouteSelectionResult selectionResult,
            String requestPath,
            GatewayRequestSemantics semantics,
            JsonNode body) {
        CanonicalRequest canonicalRequest = new CanonicalRequest(
                selectionResult.distributedKeyPrefix(),
                CanonicalIngressProtocol.from(selectionResult.protocol()),
                requestPath,
                selectionResult.requestedModel(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                body
        );
        return compileSelected(selectionResult, canonicalRequest, semantics, body);
    }

    private CanonicalExecutionPlan buildPlan(
            String protocol,
            String requestPath,
            String requestedModel,
            GatewayClientFamily clientFamily,
            GatewayRequestSemantics semantics,
            RouteSelectionResult selectionResult,
            CapabilityResolutionReport resolutionReport,
            ExecutionKind executionKind,
            String upstreamObjectMode,
            CanonicalRequest canonicalRequest,
            JsonNode requestBody,
            List<String> lossReasons,
            List<String> blockedReasons) {
        java.util.Map<String, InteropCapabilityLevel> featureLevels = new java.util.LinkedHashMap<>();
        resolutionReport.featureResolutions().forEach((key, value) -> featureLevels.put(key, value.effectiveLevel()));
        InteropCapabilityLevel executionCapabilityLevel = resolutionReport.overallEffectiveLevel();
        InteropCapabilityLevel renderCapabilityLevel = CanonicalRenderCapabilitySupport.renderLevel(protocol, requestPath, semantics);
        ExecutionBackendDecision backendDecision = executionBackendPolicyService.forCandidate(
                selectionResult == null ? null : selectionResult.selectedCandidate().candidate(),
                semantics,
                canonicalRequest,
                requestBody
        );
        return new CanonicalExecutionPlan(
                blockedReasons.isEmpty() && (!semantics.requiresRouteSelection() || selectionResult != null),
                CanonicalIngressProtocol.from(protocol),
                requestPath,
                requestedModel,
                selectionResult == null ? null : selectionResult.publicModel(),
                selectionResult == null ? null : selectionResult.resolvedModelKey(),
                semantics.resourceType(),
                semantics.operation(),
                executionKind,
                backendDecision.preferredBackend(),
                objectMode(semantics, backendDecision.preferredBackend(), upstreamObjectMode),
                backendDecision.supportedBackends(),
                backendDecision.reason(),
                executionCapabilityLevel,
                renderCapabilityLevel,
                CanonicalRenderCapabilitySupport.minimum(executionCapabilityLevel, renderCapabilityLevel),
                semantics.requiredFeatures(),
                java.util.Map.copyOf(featureLevels),
                List.copyOf(lossReasons),
                List.copyOf(blockedReasons)
        );
    }

    public CanonicalExecutionPlanCompilation compileSelected(
            RouteSelectionResult selectionResult,
            com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest resourceRequest,
            GatewayRequestSemantics semantics,
            JsonNode body) {
        CanonicalRequest canonicalRequest = new CanonicalRequest(
                resourceRequest.distributedKeyPrefix(),
                resourceRequest.ingressProtocol(),
                resourceRequest.requestPath(),
                resourceRequest.requestedModel(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                body
        );
        return compileSelected(selectionResult, canonicalRequest, semantics, body);
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

    private String resolveRequestedModel(
            String requestedModel,
            String requestPath,
            GatewayRequestSemantics semantics,
            JsonNode body) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return requestedModel.trim();
        }
        if (body != null && body.isObject()) {
            String bodyModel = body.path("model").asText(null);
            if (bodyModel != null && !bodyModel.isBlank()) {
                return bodyModel.trim();
            }
        }
        return switch (semantics.operation()) {
            case IMAGE_GENERATION, IMAGE_EDIT, IMAGE_VARIATION -> "gpt-image-1";
            case MODERATION_CREATE -> "omni-moderation-latest";
            case AUDIO_SPEECH -> "gpt-4o-mini-tts";
            case AUDIO_TRANSCRIPTION, AUDIO_TRANSLATION -> "gpt-4o-mini-transcribe";
            case FILE_CREATE, FILE_LIST, FILE_GET, FILE_CONTENT_GET, FILE_DELETE,
                    UPLOAD_CREATE, UPLOAD_GET, UPLOAD_PART_ADD, UPLOAD_COMPLETE, UPLOAD_CANCEL,
                    BATCH_CREATE, BATCH_GET, BATCH_CANCEL,
                    TUNING_CREATE, TUNING_GET, TUNING_CANCEL,
                    REALTIME_CLIENT_SECRET_CREATE -> "resource-orchestration";
            default -> throw new IllegalArgumentException("预检请求缺少 model。");
        };
    }

    private String objectMode(
            GatewayRequestSemantics semantics,
            com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend backend,
            String upstreamObjectMode) {
        if (semantics == null || backend == null) {
            return upstreamObjectMode;
        }
        return switch (backend) {
            case NATIVE -> semantics.resourceType() == TranslationResourceType.CHAT || semantics.resourceType() == TranslationResourceType.RESPONSE
                    ? upstreamObjectMode
                    : "native-resource";
            case PASSTHROUGH -> "passthrough-resource";
            case ORCHESTRATION -> "gateway-object-lineage";
            case SPRING_AI -> upstreamObjectMode;
        };
    }
}
