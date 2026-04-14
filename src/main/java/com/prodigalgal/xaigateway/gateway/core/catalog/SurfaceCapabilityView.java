package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionView;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import java.util.List;
import java.util.Map;

public record SurfaceCapabilityView(
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
                ExecutionBackend.SPRING_AI,
                List.of(ExecutionBackend.SPRING_AI),
                executionCapabilityLevel,
                renderCapabilityLevel,
                overallCapabilityLevel,
                requiredFeatures,
                featureResolutions
        );
    }
}
