package com.prodigalgal.xaigateway.gateway.core.interop;

import tools.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlanCompilation;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TranslationExecutionPlanCompiler {

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final GatewayRequestFeatureService gatewayRequestFeatureService;
    private final SiteCapabilityTruthService siteCapabilityTruthService;
    private final NonChatRoutePolicyService nonChatRoutePolicyService;
    private final NonChatTargetResolutionService nonChatTargetResolutionService;
    private final NonChatDegradationPolicyService nonChatDegradationPolicyService;

    @Autowired
    public TranslationExecutionPlanCompiler(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            SiteCapabilityTruthService siteCapabilityTruthService,
            NonChatRoutePolicyService nonChatRoutePolicyService,
            NonChatTargetResolutionService nonChatTargetResolutionService,
            NonChatDegradationPolicyService nonChatDegradationPolicyService) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.gatewayRequestFeatureService = gatewayRequestFeatureService;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
        this.nonChatRoutePolicyService = nonChatRoutePolicyService;
        this.nonChatTargetResolutionService = nonChatTargetResolutionService;
        this.nonChatDegradationPolicyService = nonChatDegradationPolicyService;
    }

    public TranslationExecutionPlanCompiler(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            SiteCapabilityTruthService siteCapabilityTruthService,
            com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendPolicyService executionBackendPolicyService) {
        this(
                gatewayRouteSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService,
                NonChatRoutePolicyService.forTests(siteCapabilityTruthService, executionBackendPolicyService),
                NonChatTargetResolutionService.createDefault(),
                new NonChatDegradationPolicyService()
        );
    }

    public TranslationExecutionPlanCompiler(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            SiteCapabilityTruthService siteCapabilityTruthService) {
        this(
                gatewayRouteSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService,
                new com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendPolicyService()
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
        String effectiveRequestPath = normalizeRequestPath(requestPath, normalizedProtocol);
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(httpMethod, effectiveRequestPath, body);
        String resolvedRequestedModel = resolveRequestedModel(requestedModel, effectiveRequestPath, semantics, body);
        List<String> blockedReasons = new ArrayList<>();
        CanonicalRequest canonicalRequest = new CanonicalRequest(
                distributedKeyPrefix,
                CanonicalIngressProtocol.from(normalizedProtocol),
                effectiveRequestPath,
                resolvedRequestedModel,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                body
        );

        RouteSelectionResult selectionResult = null;
        NonChatRoutePolicyDecision policyDecision;
        Map<String, InteropCapabilityLevel> featureLevels = Map.of();
        if (semantics.routeSelectionMode() == RouteSelectionMode.CATALOG_SELECTION) {
            try {
                selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                        distributedKeyPrefix,
                        normalizedProtocol,
                        effectiveRequestPath,
                        resolvedRequestedModel,
                        body,
                        clientFamily == null ? GatewayClientFamily.GENERIC_OPENAI : clientFamily,
                        false
                ));
            } catch (IllegalArgumentException exception) {
                blockedReasons.add(exception.getMessage());
            }
            if (selectionResult != null) {
                featureLevels = effectiveFeatureLevels(selectionResult.selectedCandidate().candidate(), semantics);
            }
            policyDecision = selectionResult == null
                    ? nonChatRoutePolicyService.evaluateWithoutCandidate(
                            normalizedProtocol,
                            effectiveRequestPath,
                            semantics,
                            canonicalRequest,
                            body,
                            "catalog_selection_unresolved",
                            blockedReasons
                    )
                    : nonChatRoutePolicyService.evaluateCandidate(
                            normalizedProtocol,
                            effectiveRequestPath,
                            semantics,
                            selectionResult.selectedCandidate().candidate(),
                            canonicalRequest,
                            body
                    );
        } else {
            NonChatTargetResolution targetResolution = nonChatTargetResolutionService.resolve(
                    distributedKeyPrefix,
                    null,
                    semantics,
                    gatewayRequestFeatureService.extractPathParams(effectiveRequestPath)
            );
            blockedReasons.addAll(targetResolution.blockedReasons());
            if (targetResolution.candidate() != null) {
                featureLevels = effectiveFeatureLevels(targetResolution.candidate(), semantics);
            }
            policyDecision = targetResolution.candidate() == null
                    ? nonChatRoutePolicyService.evaluateWithoutCandidate(
                            normalizedProtocol,
                            effectiveRequestPath,
                            semantics,
                            canonicalRequest,
                            body,
                            targetResolution.policyReason(),
                            blockedReasons
                    )
                    : nonChatRoutePolicyService.evaluateResolvedTarget(
                            normalizedProtocol,
                            effectiveRequestPath,
                            semantics,
                            targetResolution.candidate(),
                            canonicalRequest,
                            body,
                            targetResolution.policyReason(),
                            blockedReasons
                    );
        }

        NonChatDegradationOutcome degradationOutcome = nonChatDegradationPolicyService.evaluate(
                semantics,
                degradationPolicy,
                policyDecision
        );
        CanonicalExecutionPlan plan = buildPlan(
                normalizedProtocol,
                effectiveRequestPath,
                resolvedRequestedModel,
                semantics,
                selectionResult,
                canonicalRequest,
                policyDecision,
                degradationOutcome,
                featureLevels,
                degradationOutcome.lossReasons(),
                degradationOutcome.blockedReasons()
        );
        return new CanonicalExecutionPlanCompilation(plan, selectionResult, semantics, canonicalRequest);
    }

    public CanonicalExecutionPlanCompilation compileSelected(
            RouteSelectionResult selectionResult,
            CanonicalRequest canonicalRequest,
            GatewayRequestSemantics semantics,
            JsonNode body) {
        NonChatRoutePolicyDecision policyDecision = nonChatRoutePolicyService.evaluateCandidate(
                selectionResult.protocol(),
                canonicalRequest.requestPath(),
                semantics,
                selectionResult.selectedCandidate().candidate(),
                canonicalRequest,
                body
        );
        NonChatDegradationOutcome degradationOutcome = nonChatDegradationPolicyService.evaluate(
                semantics,
                GatewayDegradationPolicy.ALLOW_LOSSY,
                policyDecision
        );
        CanonicalExecutionPlan plan = buildPlan(
                selectionResult.protocol(),
                canonicalRequest.requestPath(),
                selectionResult.requestedModel(),
                semantics,
                selectionResult,
                canonicalRequest,
                policyDecision,
                degradationOutcome,
                effectiveFeatureLevels(selectionResult.selectedCandidate().candidate(), semantics),
                degradationOutcome.lossReasons(),
                degradationOutcome.blockedReasons()
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
            GatewayRequestSemantics semantics,
            RouteSelectionResult selectionResult,
            CanonicalRequest canonicalRequest,
            NonChatRoutePolicyDecision policyDecision,
            NonChatDegradationOutcome degradationOutcome,
            Map<String, InteropCapabilityLevel> featureLevels,
            List<String> lossReasons,
            List<String> blockedReasons) {
        return new CanonicalExecutionPlan(
                blockedReasons.isEmpty() && (semantics.routeSelectionMode() != RouteSelectionMode.CATALOG_SELECTION || selectionResult != null),
                CanonicalIngressProtocol.from(protocol),
                requestPath,
                semantics.normalizedPath(),
                semantics.surface(),
                requestedModel,
                selectionResult == null ? null : selectionResult.publicModel(),
                selectionResult == null ? null : selectionResult.resolvedModelKey(),
                semantics.resourceType(),
                semantics.operation(),
                blockedReasons.isEmpty() ? policyDecision.executionKind() : ExecutionKind.BLOCKED,
                policyDecision.preferredBackend(),
                degradationOutcome.supportStatus(),
                blockedReasons.isEmpty() ? policyDecision.objectMode() : "blocked",
                policyDecision.supportedBackends(),
                policyDecision.policyReason(),
                degradationOutcome.degradationLevel(),
                policyDecision.executionCapabilityLevel(),
                policyDecision.renderCapabilityLevel(),
                policyDecision.overallCapabilityLevel(),
                List.copyOf(blockedReasons),
                semantics.requiredFeatures(),
                Map.copyOf(featureLevels),
                List.copyOf(lossReasons),
                List.copyOf(blockedReasons),
                semantics.routeSelectionMode(),
                policyDecision.policyReason(),
                degradationOutcome.renderPolicyReason(),
                degradationOutcome.fallbackPolicyReason()
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
                    BATCH_LIST, BATCH_CREATE, BATCH_GET, BATCH_CANCEL,
                    ANTHROPIC_MESSAGE_BATCH_CREATE, ANTHROPIC_MESSAGE_BATCH_GET, ANTHROPIC_MESSAGE_BATCH_CANCEL,
                    TUNING_CREATE, TUNING_GET, TUNING_CANCEL, TUNING_EVENTS_LIST, TUNING_CHECKPOINTS_LIST,
                    REALTIME_CLIENT_SECRET_CREATE -> "resource-orchestration";
            default -> throw new IllegalArgumentException("预检请求缺少 model。");
        };
    }

    private Map<String, InteropCapabilityLevel> effectiveFeatureLevels(
            com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate,
            GatewayRequestSemantics semantics) {
        if (candidate == null || semantics == null) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, InteropCapabilityLevel> levels = new java.util.LinkedHashMap<>();
        siteCapabilityTruthService.resolve(candidate, semantics).featureResolutions()
                .forEach((key, value) -> levels.put(key, value.effectiveLevel()));
        return Map.copyOf(levels);
    }
}
