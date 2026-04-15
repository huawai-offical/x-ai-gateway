package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionView;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import java.util.List;
import java.util.Map;

public record SurfaceCapabilityView(
        TranslationResourceType resourceType,
        TranslationOperation operation,
        String surface,
        String normalizedPath,
        ExecutionBackend preferredBackend,
        List<ExecutionBackend> supportedBackends,
        SupportStatus supportStatus,
        InteropCapabilityLevel degradationLevel,
        InteropCapabilityLevel executionCapabilityLevel,
        InteropCapabilityLevel renderCapabilityLevel,
        InteropCapabilityLevel overallCapabilityLevel,
        List<String> blockerReasons,
        List<String> lossReasons,
        List<String> requiredFeatures,
        Map<String, CapabilityResolutionView> featureResolutions
) {
    public SurfaceCapabilityView(
            TranslationResourceType resourceType,
            TranslationOperation operation,
            ExecutionBackend preferredBackend,
            List<ExecutionBackend> supportedBackends,
            InteropCapabilityLevel executionCapabilityLevel,
            InteropCapabilityLevel renderCapabilityLevel,
            InteropCapabilityLevel overallCapabilityLevel,
            List<String> requiredFeatures,
            Map<String, CapabilityResolutionView> featureResolutions
    ) {
        this(
                resourceType,
                operation,
                defaultSurface(resourceType, operation),
                defaultNormalizedPath(resourceType, operation),
                preferredBackend,
                supportedBackends,
                SupportStatus.resolve(preferredBackend, overallCapabilityLevel, collectBlockedReasons(featureResolutions)),
                SupportStatus.normalizeDegradationLevel(overallCapabilityLevel, collectBlockedReasons(featureResolutions)),
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                collectBlockedReasons(featureResolutions),
                collectLossReasons(featureResolutions),
                requiredFeatures,
                featureResolutions
        );
    }

    public SurfaceCapabilityView(
            TranslationResourceType resourceType,
            TranslationOperation operation,
            InteropCapabilityLevel executionCapabilityLevel,
            InteropCapabilityLevel renderCapabilityLevel,
            InteropCapabilityLevel overallCapabilityLevel,
            List<String> requiredFeatures,
            Map<String, CapabilityResolutionView> featureResolutions
    ) {
        this(
                resourceType,
                operation,
                defaultSurface(resourceType, operation),
                defaultNormalizedPath(resourceType, operation),
                ExecutionBackend.SPRING_AI,
                List.of(ExecutionBackend.SPRING_AI),
                SupportStatus.resolve(ExecutionBackend.SPRING_AI, overallCapabilityLevel, collectBlockedReasons(featureResolutions)),
                SupportStatus.normalizeDegradationLevel(overallCapabilityLevel, collectBlockedReasons(featureResolutions)),
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                collectBlockedReasons(featureResolutions),
                collectLossReasons(featureResolutions),
                requiredFeatures,
                featureResolutions
        );
    }

    public SurfaceCapabilityView {
        supportedBackends = supportedBackends == null ? List.of() : List.copyOf(supportedBackends);
        blockerReasons = blockerReasons == null ? List.of() : List.copyOf(blockerReasons);
        lossReasons = lossReasons == null ? List.of() : List.copyOf(lossReasons);
        requiredFeatures = requiredFeatures == null ? List.of() : List.copyOf(requiredFeatures);
        featureResolutions = featureResolutions == null ? Map.of() : Map.copyOf(featureResolutions);
        surface = surface == null || surface.isBlank() ? defaultSurface(resourceType, operation) : surface;
        normalizedPath = normalizedPath == null || normalizedPath.isBlank()
                ? defaultNormalizedPath(resourceType, operation)
                : normalizedPath;
        supportStatus = supportStatus == null
                ? SupportStatus.resolve(preferredBackend, overallCapabilityLevel, blockerReasons)
                : supportStatus;
        degradationLevel = degradationLevel == null
                ? SupportStatus.normalizeDegradationLevel(overallCapabilityLevel, blockerReasons)
                : degradationLevel;
    }

    private static String defaultSurface(TranslationResourceType resourceType, TranslationOperation operation) {
        return new GatewayRequestSemantics(resourceType, operation, List.of(), true).surface();
    }

    private static String defaultNormalizedPath(TranslationResourceType resourceType, TranslationOperation operation) {
        return new GatewayRequestSemantics(resourceType, operation, List.of(), true).normalizedPath();
    }

    private static List<String> collectBlockedReasons(Map<String, CapabilityResolutionView> featureResolutions) {
        if (featureResolutions == null || featureResolutions.isEmpty()) {
            return List.of();
        }
        return featureResolutions.values().stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(item -> item.blockedReasons().stream())
                .distinct()
                .toList();
    }

    private static List<String> collectLossReasons(Map<String, CapabilityResolutionView> featureResolutions) {
        if (featureResolutions == null || featureResolutions.isEmpty()) {
            return List.of();
        }
        return featureResolutions.values().stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(item -> item.lossReasons().stream())
                .distinct()
                .toList();
    }
}
