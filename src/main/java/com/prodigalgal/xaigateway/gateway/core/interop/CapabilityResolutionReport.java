package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import java.util.List;
import java.util.Map;

public record CapabilityResolutionReport(
        Map<String, CapabilityResolution> featureResolutions,
        InteropCapabilityLevel overallDeclaredLevel,
        InteropCapabilityLevel overallImplementedLevel,
        InteropCapabilityLevel overallEffectiveLevel,
        ExecutionKind executionKind,
        String upstreamObjectMode,
        List<String> blockedReasons,
        List<String> lossReasons
) {
}
