package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.ResourceSurfaceRegistry;
import com.prodigalgal.xaigateway.gateway.core.interop.RouteSelectionMode;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import java.util.List;
import java.util.Map;

public record CanonicalExecutionPlan(
        boolean executable,
        CanonicalIngressProtocol ingressProtocol,
        String requestPath,
        String normalizedPath,
        String surface,
        String requestedModel,
        String publicModel,
        String resolvedModel,
        TranslationResourceType resourceType,
        TranslationOperation operation,
        ExecutionKind executionKind,
        ExecutionBackend executionBackend,
        SupportStatus supportStatus,
        String objectMode,
        List<ExecutionBackend> supportedBackends,
        String backendReason,
        InteropCapabilityLevel degradationLevel,
        InteropCapabilityLevel executionCapabilityLevel,
        InteropCapabilityLevel renderCapabilityLevel,
        InteropCapabilityLevel overallCapabilityLevel,
        List<String> blockerReasons,
        List<InteropFeature> requiredFeatures,
        Map<String, InteropCapabilityLevel> featureLevels,
        List<String> degradations,
        List<String> blockers,
        RouteSelectionMode routeSelectionMode,
        String routePolicyReason,
        String renderPolicyReason,
        String fallbackPolicyReason
) {
    public CanonicalExecutionPlan {
        resourceType = resourceType == null ? TranslationResourceType.UNKNOWN : resourceType;
        operation = operation == null ? TranslationOperation.UNKNOWN : operation;
        normalizedPath = normalizedPath == null || normalizedPath.isBlank()
                ? (requestPath == null || requestPath.isBlank() ? defaultNormalizedPath(resourceType, operation) : requestPath)
                : normalizedPath;
        surface = surface == null || surface.isBlank() ? defaultSurface(resourceType, operation) : surface;
        supportedBackends = supportedBackends == null ? List.of() : List.copyOf(supportedBackends);
        blockerReasons = blockerReasons == null ? (blockers == null ? List.of() : List.copyOf(blockers)) : List.copyOf(blockerReasons);
        requiredFeatures = requiredFeatures == null ? List.of() : List.copyOf(requiredFeatures);
        featureLevels = featureLevels == null ? Map.of() : Map.copyOf(featureLevels);
        degradations = degradations == null ? List.of() : List.copyOf(degradations);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        routeSelectionMode = routeSelectionMode == null ? RouteSelectionMode.CATALOG_SELECTION : routeSelectionMode;
        routePolicyReason = routePolicyReason == null ? "" : routePolicyReason;
        renderPolicyReason = renderPolicyReason == null ? "" : renderPolicyReason;
        fallbackPolicyReason = fallbackPolicyReason == null ? "" : fallbackPolicyReason;
        degradationLevel = degradationLevel == null
                ? SupportStatus.normalizeDegradationLevel(overallCapabilityLevel, blockerReasons)
                : degradationLevel;
        supportStatus = supportStatus == null
                ? SupportStatus.resolve(executionBackend, degradationLevel, blockerReasons)
                : supportStatus;
    }

    public CanonicalExecutionPlan(
            boolean executable,
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            String requestedModel,
            String publicModel,
            String resolvedModel,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            ExecutionKind executionKind,
            InteropCapabilityLevel executionCapabilityLevel,
            InteropCapabilityLevel renderCapabilityLevel,
            InteropCapabilityLevel overallCapabilityLevel,
            List<InteropFeature> requiredFeatures,
            Map<String, InteropCapabilityLevel> featureLevels,
            List<String> degradations,
            List<String> blockers
    ) {
        this(
                executable,
                ingressProtocol,
                requestPath,
                requestPath,
                defaultSurface(resourceType, operation),
                requestedModel,
                publicModel,
                resolvedModel,
                resourceType,
                operation,
                executionKind,
                ExecutionBackend.SPRING_AI,
                null,
                null,
                List.of(ExecutionBackend.SPRING_AI),
                "legacy_default",
                null,
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                blockers,
                requiredFeatures,
                featureLevels,
                degradations,
                blockers,
                RouteSelectionMode.CATALOG_SELECTION,
                "",
                "",
                ""
        );
    }

    public CanonicalExecutionPlan(
            boolean executable,
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            String requestedModel,
            String publicModel,
            String resolvedModel,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            ExecutionKind executionKind,
            ExecutionBackend executionBackend,
            List<ExecutionBackend> supportedBackends,
            String backendReason,
            InteropCapabilityLevel executionCapabilityLevel,
            InteropCapabilityLevel renderCapabilityLevel,
            InteropCapabilityLevel overallCapabilityLevel,
            List<InteropFeature> requiredFeatures,
            Map<String, InteropCapabilityLevel> featureLevels,
            List<String> degradations,
            List<String> blockers
    ) {
        this(
                executable,
                ingressProtocol,
                requestPath,
                requestPath,
                defaultSurface(resourceType, operation),
                requestedModel,
                publicModel,
                resolvedModel,
                resourceType,
                operation,
                executionKind,
                executionBackend,
                null,
                null,
                supportedBackends,
                backendReason,
                null,
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                blockers,
                requiredFeatures,
                featureLevels,
                degradations,
                blockers,
                RouteSelectionMode.CATALOG_SELECTION,
                "",
                "",
                ""
        );
    }

    public CanonicalExecutionPlan(
            boolean executable,
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            String normalizedPath,
            String surface,
            String requestedModel,
            String publicModel,
            String resolvedModel,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            ExecutionKind executionKind,
            ExecutionBackend executionBackend,
            String objectMode,
            List<ExecutionBackend> supportedBackends,
            String backendReason,
            InteropCapabilityLevel executionCapabilityLevel,
            InteropCapabilityLevel renderCapabilityLevel,
            InteropCapabilityLevel overallCapabilityLevel,
            List<InteropFeature> requiredFeatures,
            Map<String, InteropCapabilityLevel> featureLevels,
            List<String> degradations,
            List<String> blockers
    ) {
        this(
                executable,
                ingressProtocol,
                requestPath,
                normalizedPath,
                surface,
                requestedModel,
                publicModel,
                resolvedModel,
                resourceType,
                operation,
                executionKind,
                executionBackend,
                null,
                objectMode,
                supportedBackends,
                backendReason,
                null,
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                blockers,
                requiredFeatures,
                featureLevels,
                degradations,
                blockers,
                RouteSelectionMode.CATALOG_SELECTION,
                "",
                "",
                ""
        );
    }

    public CanonicalExecutionPlan(
            boolean executable,
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            String normalizedPath,
            String surface,
            String requestedModel,
            String publicModel,
            String resolvedModel,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            ExecutionKind executionKind,
            ExecutionBackend executionBackend,
            SupportStatus supportStatus,
            String objectMode,
            List<ExecutionBackend> supportedBackends,
            String backendReason,
            InteropCapabilityLevel degradationLevel,
            InteropCapabilityLevel executionCapabilityLevel,
            InteropCapabilityLevel renderCapabilityLevel,
            InteropCapabilityLevel overallCapabilityLevel,
            List<String> blockerReasons,
            List<InteropFeature> requiredFeatures,
            Map<String, InteropCapabilityLevel> featureLevels,
            List<String> degradations,
            List<String> blockers
    ) {
        this(
                executable,
                ingressProtocol,
                requestPath,
                normalizedPath,
                surface,
                requestedModel,
                publicModel,
                resolvedModel,
                resourceType,
                operation,
                executionKind,
                executionBackend,
                supportStatus,
                objectMode,
                supportedBackends,
                backendReason,
                degradationLevel,
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                blockerReasons,
                requiredFeatures,
                featureLevels,
                degradations,
                blockers,
                RouteSelectionMode.CATALOG_SELECTION,
                "",
                "",
                ""
        );
    }

    private static String defaultSurface(TranslationResourceType resourceType, TranslationOperation operation) {
        return ResourceSurfaceRegistry.defaultSurface(resourceType, operation);
    }

    private static String defaultNormalizedPath(TranslationResourceType resourceType, TranslationOperation operation) {
        return ResourceSurfaceRegistry.defaultNormalizedPath(resourceType, operation);
    }
}
