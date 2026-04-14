package com.prodigalgal.xaigateway.gateway.core.interop;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRenderCapabilitySupport;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import java.util.List;
import java.util.Map;

public record TranslationExecutionPlan(
        boolean executable,
        String protocol,
        String requestPath,
        String requestedModel,
        String publicModel,
        String resolvedModelKey,
        GatewayClientFamily clientFamily,
        TranslationResourceType resourceType,
        TranslationOperation operation,
        List<InteropFeature> requiredFeatures,
        Map<String, InteropCapabilityLevel> featureLevels,
        Map<String, CapabilityResolutionView> featureResolutions,
        RouteSelectionSource selectionSource,
        ProviderFamily providerFamily,
        Long siteProfileId,
        ExecutionKind executionKind,
        InteropCapabilityLevel overallDeclaredLevel,
        InteropCapabilityLevel overallImplementedLevel,
        InteropCapabilityLevel overallEffectiveLevel,
        InteropCapabilityLevel capabilityLevel,
        String upstreamObjectMode,
        List<String> lossReasons,
        List<String> blockedReasons,
        AuthStrategy authStrategy,
        PathStrategy pathStrategy,
        ErrorSchemaStrategy errorSchemaStrategy,
        TranslationExecutionRequestMapping requestMapping,
        TranslationExecutionResponseMapping responseMapping
) {
    public TranslationExecutionPlan(
            boolean executable,
            String protocol,
            String requestPath,
            String requestedModel,
            String publicModel,
            String resolvedModelKey,
            GatewayClientFamily clientFamily,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            List<InteropFeature> requiredFeatures,
            Map<String, InteropCapabilityLevel> featureLevels,
            RouteSelectionSource selectionSource,
            ProviderFamily providerFamily,
            Long siteProfileId,
            ExecutionKind executionKind,
            InteropCapabilityLevel capabilityLevel,
            String upstreamObjectMode,
            List<String> lossReasons,
            List<String> blockedReasons,
            AuthStrategy authStrategy,
            PathStrategy pathStrategy,
            ErrorSchemaStrategy errorSchemaStrategy,
            TranslationExecutionRequestMapping requestMapping,
            TranslationExecutionResponseMapping responseMapping
    ) {
        this(
                executable,
                protocol,
                requestPath,
                requestedModel,
                publicModel,
                resolvedModelKey,
                clientFamily,
                resourceType,
                operation,
                requiredFeatures,
                featureLevels,
                Map.of(),
                selectionSource,
                providerFamily,
                siteProfileId,
                executionKind,
                capabilityLevel,
                capabilityLevel,
                capabilityLevel,
                capabilityLevel,
                upstreamObjectMode,
                lossReasons,
                blockedReasons,
                authStrategy,
                pathStrategy,
                errorSchemaStrategy,
                requestMapping,
                responseMapping
        );
    }

    public TranslationExecutionPlan(
            boolean executable,
            String resourceType,
            String operation,
            ProviderFamily providerFamily,
            Long siteProfileId,
            ExecutionKind executionKind,
            InteropCapabilityLevel capabilityLevel,
            String upstreamObjectMode,
            List<String> lossReasons,
            List<String> blockedReasons,
            AuthStrategy authStrategy,
            PathStrategy pathStrategy,
            ErrorSchemaStrategy errorSchemaStrategy,
            Map<String, Object> requestMapping,
            Map<String, Object> responseMapping
    ) {
        this(
                executable,
                requestMapping == null ? null : requestMapping.get("protocol") instanceof String value ? value : null,
                requestMapping == null ? null : requestMapping.get("requestPath") instanceof String value ? value : null,
                requestMapping == null ? null : requestMapping.get("requestedModel") instanceof String value ? value : null,
                responseMapping == null ? null : responseMapping.get("publicModel") instanceof String value ? value : null,
                requestMapping == null ? null : requestMapping.get("resolvedModelKey") instanceof String value ? value : null,
                null,
                TranslationResourceType.fromWireName(resourceType),
                TranslationOperation.fromWireName(operation),
                List.of(),
                Map.of(),
                Map.of(),
                null,
                providerFamily,
                siteProfileId,
                executionKind,
                capabilityLevel,
                capabilityLevel,
                capabilityLevel,
                capabilityLevel,
                upstreamObjectMode,
                lossReasons == null ? List.of() : List.copyOf(lossReasons),
                blockedReasons == null ? List.of() : List.copyOf(blockedReasons),
                authStrategy,
                pathStrategy,
                errorSchemaStrategy,
                TranslationExecutionRequestMapping.fromLegacy(requestMapping),
                TranslationExecutionResponseMapping.fromLegacy(responseMapping)
        );
    }

    @JsonProperty("renderCapabilityLevel")
    public InteropCapabilityLevel renderCapabilityLevel() {
        return CanonicalRenderCapabilitySupport.renderLevel(
                protocol,
                requestPath,
                new GatewayRequestSemantics(
                        resourceType,
                        operation,
                        requiredFeatures == null ? List.of() : requiredFeatures,
                        true
                )
        );
    }

    @JsonProperty("canonicalExecutionPlan")
    public CanonicalExecutionPlan canonicalExecutionPlan() {
        InteropCapabilityLevel executionLevel = overallEffectiveLevel == null
                ? InteropCapabilityLevel.UNSUPPORTED
                : overallEffectiveLevel;
        InteropCapabilityLevel renderLevel = renderCapabilityLevel();
        return new CanonicalExecutionPlan(
                executable,
                CanonicalIngressProtocol.from(protocol),
                requestPath,
                requestedModel,
                publicModel,
                resolvedModelKey,
                resourceType,
                operation,
                executionKind,
                ExecutionBackend.SPRING_AI,
                List.of(ExecutionBackend.SPRING_AI),
                "legacy_translation_plan",
                executionLevel,
                renderLevel,
                CanonicalRenderCapabilitySupport.minimum(executionLevel, renderLevel),
                requiredFeatures == null ? List.of() : List.copyOf(requiredFeatures),
                featureLevels == null ? Map.of() : Map.copyOf(featureLevels),
                lossReasons == null ? List.of() : List.copyOf(lossReasons),
                blockedReasons == null ? List.of() : List.copyOf(blockedReasons)
        );
    }
}
