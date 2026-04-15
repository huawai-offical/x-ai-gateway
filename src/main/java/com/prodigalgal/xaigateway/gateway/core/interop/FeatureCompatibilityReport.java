package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import java.util.List;
import java.util.Map;

public record FeatureCompatibilityReport(
        Map<String, InteropCapabilityLevel> featureLevels,
        InteropCapabilityLevel capabilityLevel,
        SupportStatus supportStatus,
        InteropCapabilityLevel degradationLevel,
        List<String> lossReasons,
        List<String> blockedReasons,
        ExecutionKind executionKind,
        String upstreamObjectMode
) {
    public FeatureCompatibilityReport(
            Map<String, InteropCapabilityLevel> featureLevels,
            InteropCapabilityLevel capabilityLevel,
            List<String> lossReasons,
            List<String> blockedReasons,
            ExecutionKind executionKind,
            String upstreamObjectMode
    ) {
        this(
                featureLevels,
                capabilityLevel,
                SupportStatus.fromLevel(capabilityLevel, blockedReasons),
                SupportStatus.normalizeDegradationLevel(capabilityLevel, blockedReasons),
                lossReasons,
                blockedReasons,
                executionKind,
                upstreamObjectMode
        );
    }

    public FeatureCompatibilityReport {
        featureLevels = featureLevels == null ? Map.of() : Map.copyOf(featureLevels);
        lossReasons = lossReasons == null ? List.of() : List.copyOf(lossReasons);
        blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
        supportStatus = supportStatus == null ? SupportStatus.fromLevel(capabilityLevel, blockedReasons) : supportStatus;
        degradationLevel = degradationLevel == null
                ? SupportStatus.normalizeDegradationLevel(capabilityLevel, blockedReasons)
                : degradationLevel;
    }
}
