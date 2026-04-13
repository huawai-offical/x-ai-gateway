package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import java.util.List;
import java.util.Map;

public record FeatureCompatibilityReport(
        Map<String, InteropCapabilityLevel> featureLevels,
        InteropCapabilityLevel capabilityLevel,
        List<String> lossReasons,
        List<String> blockedReasons,
        ExecutionKind executionKind,
        String upstreamObjectMode
) {
}
