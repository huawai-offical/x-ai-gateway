package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
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
                requestedModel,
                publicModel,
                resolvedModel,
                resourceType,
                operation,
                executionKind,
                ExecutionBackend.SPRING_AI,
                null,
                List.of(ExecutionBackend.SPRING_AI),
                "legacy_default",
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                requiredFeatures,
                featureLevels,
                degradations,
                blockers
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
                requestedModel,
                publicModel,
                resolvedModel,
                resourceType,
                operation,
                executionKind,
                executionBackend,
                null,
                supportedBackends,
                backendReason,
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                requiredFeatures,
                featureLevels,
                degradations,
                blockers
        );
    }
}
