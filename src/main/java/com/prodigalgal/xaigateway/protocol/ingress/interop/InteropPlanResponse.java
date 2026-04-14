package com.prodigalgal.xaigateway.protocol.ingress.interop;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRenderCapabilitySupport;
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionView;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import java.util.List;
import java.util.Map;

public record InteropPlanResponse(
        boolean executable,
        String protocol,
        String requestPath,
        String requestedModel,
        String publicModel,
        String resolvedModelKey,
        String degradationPolicy,
        List<String> requiredFeatures,
        List<String> blockers,
        List<String> degradations,
        String resourceType,
        String operation,
        ProviderFamily providerFamily,
        Long siteProfileId,
        ExecutionKind executionKind,
        String overallDeclaredLevel,
        String overallImplementedLevel,
        String overallEffectiveLevel,
        String capabilityLevel,
        Map<String, CapabilityResolutionView> featureResolutions,
        String upstreamObjectMode,
        AuthStrategy authStrategy,
        PathStrategy pathStrategy,
        ErrorSchemaStrategy errorSchemaStrategy,
        RouteSelectionResult selectionResult,
        Map<String, Object> summary,
        Map<String, Object> debug
) {
    public InteropPlanResponse(
            boolean executable,
            String protocol,
            String requestPath,
            String requestedModel,
            String degradationPolicy,
            List<String> requiredFeatures,
            List<String> blockers,
            List<String> degradations,
            String resourceType,
            String operation,
            RouteSelectionResult selectionResult,
            Map<String, Object> summary,
            Map<String, Object> debug
    ) {
        this(
                executable,
                protocol,
                requestPath,
                requestedModel,
                null,
                null,
                degradationPolicy,
                requiredFeatures,
                blockers,
                degradations,
                resourceType,
                operation,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                null,
                null,
                null,
                null,
                selectionResult,
                summary,
                debug
        );
    }

    public static InteropPlanResponse from(
            TranslationExecutionPlan plan,
            String degradationPolicy,
            RouteSelectionResult selectionResult,
            Map<String, Object> summary,
            Map<String, Object> debug
    ) {
        return new InteropPlanResponse(
                plan.executable(),
                plan.protocol(),
                plan.requestPath(),
                plan.requestedModel(),
                plan.publicModel(),
                plan.resolvedModelKey(),
                degradationPolicy,
                plan.requiredFeatures().stream().map(InteropFeature::wireName).toList(),
                plan.blockedReasons(),
                plan.lossReasons(),
                plan.resourceType().wireName(),
                plan.operation().wireName(),
                plan.providerFamily(),
                plan.siteProfileId(),
                plan.executionKind(),
                wire(plan.overallDeclaredLevel()),
                wire(plan.overallImplementedLevel()),
                wire(plan.overallEffectiveLevel()),
                wire(plan.capabilityLevel()),
                plan.featureResolutions(),
                plan.upstreamObjectMode(),
                plan.authStrategy(),
                plan.pathStrategy(),
                plan.errorSchemaStrategy(),
                selectionResult,
                summary,
                debug
        );
    }

    private static String wire(com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel level) {
        return level == null ? null : level.name().toLowerCase();
    }

    @JsonProperty("renderCapabilityLevel")
    public String renderCapabilityLevel() {
        return wire(CanonicalRenderCapabilitySupport.renderLevel(
                protocol,
                requestPath,
                new GatewayRequestSemantics(
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.fromWireName(resourceType),
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation.fromWireName(operation),
                        parsedRequiredFeatures(),
                        true
                )
        ));
    }

    @JsonProperty("canonicalExecutionPlan")
    public CanonicalExecutionPlan canonicalExecutionPlan() {
        com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel executionLevel = overallEffectiveLevel == null
                ? com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel.UNSUPPORTED
                : com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel.valueOf(overallEffectiveLevel.toUpperCase());
        com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel renderLevel = CanonicalRenderCapabilitySupport.renderLevel(
                protocol,
                requestPath,
                new GatewayRequestSemantics(
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.fromWireName(resourceType),
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation.fromWireName(operation),
                        parsedRequiredFeatures(),
                        true
                )
        );
        java.util.Map<String, com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel> levels = new java.util.LinkedHashMap<>();
        if (featureResolutions != null) {
            featureResolutions.forEach((key, value) -> {
                if (value == null || value.effectiveLevel() == null || value.effectiveLevel().isBlank()) {
                    return;
                }
                levels.put(key, com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel.valueOf(value.effectiveLevel().toUpperCase()));
            });
        }
        return new CanonicalExecutionPlan(
                executable,
                CanonicalIngressProtocol.from(protocol),
                requestPath,
                requestedModel,
                publicModel,
                resolvedModelKey,
                com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.fromWireName(resourceType),
                com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation.fromWireName(operation),
                executionKind,
                executionLevel,
                renderLevel,
                CanonicalRenderCapabilitySupport.minimum(executionLevel, renderLevel),
                parsedRequiredFeatures(),
                Map.copyOf(levels),
                degradations == null ? List.of() : List.copyOf(degradations),
                blockers == null ? List.of() : List.copyOf(blockers)
        );
    }

    private List<com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature> parsedRequiredFeatures() {
        return requiredFeatures == null
                ? List.of()
                : requiredFeatures.stream()
                        .map(com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature::fromWireName)
                        .toList();
    }
}
