package com.prodigalgal.xaigateway.gateway.core.interop;

import java.util.List;
import java.util.Map;

public record SurfaceCompatibilityReport(
        Map<String, CapabilityResolution> featureResolutions,
        InteropCapabilityLevel executionCapabilityLevel,
        List<String> blockedReasons,
        List<String> lossReasons
) {
    public SurfaceCompatibilityReport {
        featureResolutions = featureResolutions == null ? Map.of() : Map.copyOf(featureResolutions);
        blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
        lossReasons = lossReasons == null ? List.of() : List.copyOf(lossReasons);
    }
}
