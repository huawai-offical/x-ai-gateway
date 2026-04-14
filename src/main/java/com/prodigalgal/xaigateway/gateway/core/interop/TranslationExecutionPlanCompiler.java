package com.prodigalgal.xaigateway.gateway.core.interop;

import tools.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class TranslationExecutionPlanCompiler {

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final GatewayRequestFeatureService gatewayRequestFeatureService;
    private final SiteCapabilityTruthService siteCapabilityTruthService;

    public TranslationExecutionPlanCompiler(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            SiteCapabilityTruthService siteCapabilityTruthService) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.gatewayRequestFeatureService = gatewayRequestFeatureService;
        this.siteCapabilityTruthService = siteCapabilityTruthService;
    }

    public TranslationExecutionPlanCompilation compilePreview(
            String distributedKeyPrefix,
            String protocol,
            String requestPath,
            String requestedModel,
            GatewayDegradationPolicy degradationPolicy,
            GatewayClientFamily clientFamily,
            JsonNode body) {
        String normalizedProtocol = normalizeProtocol(protocol);
        String normalizedRequestPath = normalizeRequestPath(requestPath, normalizedProtocol);
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(normalizedRequestPath, body);
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
        } else {
            blockedReasons.add("当前操作依赖资源编排上下文，暂不支持独立预检选路。");
        }

        CapabilityResolutionReport report = selectionResult == null
                ? new CapabilityResolutionReport(
                        java.util.Map.of(),
                        InteropCapabilityLevel.UNSUPPORTED,
                        InteropCapabilityLevel.UNSUPPORTED,
                        InteropCapabilityLevel.UNSUPPORTED,
                        ExecutionKind.BLOCKED,
                        "blocked",
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

        TranslationExecutionPlan plan = buildPlan(
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
                List.copyOf(lossReasons),
                List.copyOf(blockedReasons)
        );
        return new TranslationExecutionPlanCompilation(plan, selectionResult, semantics);
    }

    public TranslationExecutionPlan compileSelected(
            RouteSelectionResult selectionResult,
            String requestPath,
            GatewayRequestSemantics semantics,
            JsonNode body) {
        CapabilityResolutionReport report = siteCapabilityTruthService.resolve(selectionResult.selectedCandidate().candidate(), semantics);
        return buildPlan(
                selectionResult.protocol(),
                requestPath,
                selectionResult.requestedModel(),
                selectionResult.clientFamily(),
                semantics,
                selectionResult,
                report,
                report.executionKind(),
                report.upstreamObjectMode(),
                report.lossReasons(),
                report.blockedReasons()
        );
    }

    private TranslationExecutionPlan buildPlan(
            String protocol,
            String requestPath,
            String requestedModel,
            GatewayClientFamily clientFamily,
            GatewayRequestSemantics semantics,
            RouteSelectionResult selectionResult,
            CapabilityResolutionReport resolutionReport,
            ExecutionKind executionKind,
            String upstreamObjectMode,
            List<String> lossReasons,
            List<String> blockedReasons) {
        java.util.Map<String, InteropCapabilityLevel> featureLevels = new java.util.LinkedHashMap<>();
        java.util.Map<String, CapabilityResolutionView> featureResolutionViews = new java.util.LinkedHashMap<>();
        resolutionReport.featureResolutions().forEach((key, value) -> {
            featureLevels.put(key, value.effectiveLevel());
            featureResolutionViews.put(key, CapabilityResolutionView.from(value));
        });
        return new TranslationExecutionPlan(
                blockedReasons.isEmpty(),
                protocol,
                requestPath,
                requestedModel,
                selectionResult == null ? null : selectionResult.publicModel(),
                selectionResult == null ? null : selectionResult.resolvedModelKey(),
                clientFamily,
                semantics.resourceType(),
                semantics.operation(),
                semantics.requiredFeatures(),
                java.util.Map.copyOf(featureLevels),
                java.util.Map.copyOf(featureResolutionViews),
                selectionResult == null ? null : selectionResult.selectionSource(),
                selectionResult == null ? null : selectionResult.selectedCandidate().candidate().providerFamily(),
                selectionResult == null ? null : selectionResult.selectedCandidate().candidate().siteProfileId(),
                executionKind,
                resolutionReport.overallDeclaredLevel(),
                resolutionReport.overallImplementedLevel(),
                resolutionReport.overallEffectiveLevel(),
                resolutionReport.overallEffectiveLevel(),
                upstreamObjectMode,
                lossReasons,
                blockedReasons,
                selectionResult == null ? null : selectionResult.selectedCandidate().candidate().authStrategy(),
                selectionResult == null ? null : selectionResult.selectedCandidate().candidate().pathStrategy(),
                selectionResult == null ? null : selectionResult.selectedCandidate().candidate().errorSchemaStrategy(),
                new TranslationExecutionRequestMapping(
                        protocol,
                        requestPath,
                        requestedModel,
                        selectionResult == null ? null : selectionResult.publicModel(),
                        selectionResult == null ? null : selectionResult.resolvedModelKey(),
                        clientFamily,
                        semantics.requiredFeatures(),
                        java.util.Map.copyOf(featureLevels)
                ),
                new TranslationExecutionResponseMapping(
                        selectionResult == null ? null : selectionResult.selectionSource(),
                        selectionResult == null ? null : selectionResult.selectedCandidate().candidate().providerFamily(),
                        selectionResult == null ? null : selectionResult.selectedCandidate().candidate().siteProfileId(),
                        executionKind,
                        resolutionReport.overallEffectiveLevel(),
                        upstreamObjectMode,
                        selectionResult == null ? null : selectionResult.selectedCandidate().candidate().authStrategy(),
                        selectionResult == null ? null : selectionResult.selectedCandidate().candidate().pathStrategy(),
                        selectionResult == null ? null : selectionResult.selectedCandidate().candidate().errorSchemaStrategy()
                )
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
            case FILE_CREATE, UPLOAD_CREATE, UPLOAD_PART_ADD, UPLOAD_COMPLETE, UPLOAD_CANCEL, BATCH_CREATE, BATCH_CANCEL,
                    REALTIME_CLIENT_SECRET_CREATE -> "resource-orchestration";
            default -> throw new IllegalArgumentException("预检请求缺少 model。");
        };
    }
}
