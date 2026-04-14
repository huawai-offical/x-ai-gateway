package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
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
        InteropCapabilityLevel executionCapabilityLevel,
        InteropCapabilityLevel renderCapabilityLevel,
        InteropCapabilityLevel overallCapabilityLevel,
        List<InteropFeature> requiredFeatures,
        Map<String, InteropCapabilityLevel> featureLevels,
        List<String> degradations,
        List<String> blockers
) {
}
