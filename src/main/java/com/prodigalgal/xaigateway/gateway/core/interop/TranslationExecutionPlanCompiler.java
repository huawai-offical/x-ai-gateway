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
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
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
    private final LosslessTranslationMatrixService losslessTranslationMatrixService;

    @Autowired
    public TranslationExecutionPlanCompiler(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            SiteCapabilityTruthService siteCapabilityTruthService,
            NonChatRoutePolicyService nonChatRoutePolicyService,
            NonChatTargetResolutionService nonChatTargetResolutionService,
            NonChatDegradationPolicyService nonChatDegradationPolicyService,
            LosslessTranslationMatrixService losslessTranslationMatrixService) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.gatewayRequestFeatureService = gatewayRequestFeatureService;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
        this.nonChatRoutePolicyService = nonChatRoutePolicyService;
        this.nonChatTargetResolutionService = nonChatTargetResolutionService;
        this.nonChatDegradationPolicyService = nonChatDegradationPolicyService;
        this.losslessTranslationMatrixService = losslessTranslationMatrixService;
    }

    public TranslationExecutionPlanCompiler(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            SiteCapabilityTruthService siteCapabilityTruthService,
            NonChatRoutePolicyService nonChatRoutePolicyService,
            NonChatTargetResolutionService nonChatTargetResolutionService,
            NonChatDegradationPolicyService nonChatDegradationPolicyService) {
        this(
                gatewayRouteSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService,
                nonChatRoutePolicyService,
                nonChatTargetResolutionService,
                nonChatDegradationPolicyService,
                new LosslessTranslationMatrixService()
        );
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
                new NonChatDegradationPolicyService(),
                new LosslessTranslationMatrixService()
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
                        false,
                        null,
                        httpMethod
                ));
            } catch (IllegalArgumentException exception) {
                blockedReasons.add(exception.getMessage());
            }
            if (selectionResult != null) {
                featureLevels = effectiveFeatureLevels(selectionResult.selectedCandidate().candidate(), semantics);
                blockedReasons.addAll(losslessTranslationBlockers(
                        CanonicalIngressProtocol.from(normalizedProtocol),
                        targetProtocol(CanonicalIngressProtocol.from(normalizedProtocol), selectionResult.selectedCandidate().candidate()),
                        semantics,
                        body
                ));
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
                blockedReasons.addAll(losslessTranslationBlockers(
                        CanonicalIngressProtocol.from(normalizedProtocol),
                        targetProtocol(CanonicalIngressProtocol.from(normalizedProtocol), targetResolution.candidate()),
                        semantics,
                        body
                ));
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
        List<String> finalBlockedReasons = mergeReasons(degradationOutcome.blockedReasons(), blockedReasons);
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
                finalBlockedReasons
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
        List<String> matrixBlockers = losslessTranslationBlockers(
                canonicalRequest.ingressProtocol(),
                targetProtocol(canonicalRequest.ingressProtocol(), selectionResult.selectedCandidate().candidate()),
                semantics,
                body
        );
        NonChatDegradationOutcome degradationOutcome = nonChatDegradationPolicyService.evaluate(
                semantics,
                GatewayDegradationPolicy.STRICT,
                policyDecision
        );
        List<String> blockedReasons = mergeReasons(degradationOutcome.blockedReasons(), matrixBlockers);
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
                blockedReasons
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
                SupportStatus.resolve(policyDecision.preferredBackend(), policyDecision.overallCapabilityLevel(), blockedReasons),
                blockedReasons.isEmpty() ? policyDecision.objectMode() : "blocked",
                policyDecision.supportedBackends(),
                policyDecision.policyReason(),
                SupportStatus.normalizeDegradationLevel(policyDecision.overallCapabilityLevel(), blockedReasons),
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
        return ResourceSurfaceRegistry.defaultModel(semantics.operation())
                .orElseThrow(() -> new IllegalArgumentException("预检请求缺少 model。"));
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

    private List<String> losslessTranslationBlockers(
            CanonicalIngressProtocol sourceProtocol,
            CanonicalIngressProtocol targetProtocol,
            GatewayRequestSemantics semantics,
            JsonNode body) {
        return losslessTranslationMatrixService.blockingEntriesForRequest(sourceProtocol, targetProtocol, semantics, body).stream()
                .map(entry -> "跨协议属性 " + entry.attributePath()
                        + " 不能从 " + protocolName(entry.sourceProtocol())
                        + " 无损翻译到 " + protocolName(entry.targetProtocol())
                        + "；" + entry.requirement()
                        + " failure_code=" + entry.failureCode())
                .distinct()
                .toList();
    }

    private CanonicalIngressProtocol targetProtocol(
            CanonicalIngressProtocol sourceProtocol,
            com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate) {
        if (candidate == null) {
            return CanonicalIngressProtocol.UNKNOWN;
        }
        UpstreamSiteKind siteKind = candidate.siteKind();
        ProviderType providerType = candidate.providerType();
        if (sourceProtocol == CanonicalIngressProtocol.RESPONSES
                && isOpenAiStyleProvider(providerType, siteKind)
                && siteKind != UpstreamSiteKind.OPENAI_DIRECT) {
            return CanonicalIngressProtocol.OPENAI;
        }
        if (isOpenAiStyleProvider(providerType, siteKind) && supportsIngressProtocol(sourceProtocol, candidate)) {
            return sourceProtocol;
        }
        if (siteKind != null) {
            return switch (siteKind) {
                case ANTHROPIC_DIRECT -> CanonicalIngressProtocol.ANTHROPIC_NATIVE;
                case GEMINI_DIRECT, VERTEX_AI -> CanonicalIngressProtocol.GOOGLE_NATIVE;
                case OPENAI_DIRECT, AZURE_OPENAI, OPENAI_COMPATIBLE_GENERIC, XIAOMI_MIMO, DEEPSEEK, QWEN, MOONSHOT,
                        SILICONFLOW, VOLCENGINE, MINIMAX, DIFY, GROK, MISTRAL, COHERE, JINA, TOGETHER,
                        FIREWORKS, OPENROUTER, PERPLEXITY -> CanonicalIngressProtocol.OPENAI;
                case OLLAMA_DIRECT -> CanonicalIngressProtocol.UNKNOWN;
            };
        }
        if (providerType == null) {
            return CanonicalIngressProtocol.UNKNOWN;
        }
        return switch (providerType) {
            case ANTHROPIC_DIRECT -> CanonicalIngressProtocol.ANTHROPIC_NATIVE;
            case GEMINI_DIRECT -> CanonicalIngressProtocol.GOOGLE_NATIVE;
            case OPENAI_DIRECT, OPENAI_COMPATIBLE -> CanonicalIngressProtocol.OPENAI;
            case OLLAMA_DIRECT -> CanonicalIngressProtocol.UNKNOWN;
        };
    }

    private boolean isOpenAiStyleProvider(ProviderType providerType, UpstreamSiteKind siteKind) {
        if (providerType == ProviderType.OPENAI_DIRECT || providerType == ProviderType.OPENAI_COMPATIBLE) {
            return true;
        }
        return siteKind == UpstreamSiteKind.OPENAI_DIRECT
                || siteKind == UpstreamSiteKind.AZURE_OPENAI
                || siteKind == UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC
                || siteKind == UpstreamSiteKind.XIAOMI_MIMO
                || siteKind == UpstreamSiteKind.DEEPSEEK
                || siteKind == UpstreamSiteKind.QWEN
                || siteKind == UpstreamSiteKind.MOONSHOT
                || siteKind == UpstreamSiteKind.VOLCENGINE
                || siteKind == UpstreamSiteKind.MINIMAX
                || siteKind == UpstreamSiteKind.GROK
                || siteKind == UpstreamSiteKind.MISTRAL
                || siteKind == UpstreamSiteKind.COHERE
                || siteKind == UpstreamSiteKind.JINA
                || siteKind == UpstreamSiteKind.PERPLEXITY;
    }

    private boolean supportsIngressProtocol(
            CanonicalIngressProtocol sourceProtocol,
            com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate) {
        if (sourceProtocol == null || sourceProtocol == CanonicalIngressProtocol.UNKNOWN || candidate.supportedProtocols() == null) {
            return false;
        }
        String wireName = switch (sourceProtocol) {
            case OPENAI -> "openai";
            case RESPONSES -> "responses";
            case ANTHROPIC_NATIVE -> "anthropic_native";
            case GOOGLE_NATIVE -> "google_native";
            case UNKNOWN -> "unknown";
        };
        return candidate.supportedProtocols().stream().anyMatch(protocol -> wireName.equalsIgnoreCase(protocol));
    }

    private String protocolName(CanonicalIngressProtocol protocol) {
        return protocol == null ? "unknown" : protocol.name().toLowerCase(Locale.ROOT);
    }

    private List<String> mergeReasons(List<String> left, List<String> right) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        if (left != null) {
            merged.addAll(left);
        }
        if (right != null) {
            merged.addAll(right);
        }
        return List.copyOf(merged);
    }
}
