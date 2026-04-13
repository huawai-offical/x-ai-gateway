package com.prodigalgal.xaigateway.gateway.core.interop;

import java.util.List;

public record CapabilityResolution(
        InteropFeature feature,
        InteropCapabilityLevel declaredLevel,
        InteropCapabilityLevel modelLevel,
        InteropCapabilityLevel implementedLevel,
        InteropCapabilityLevel effectiveLevel,
        List<String> blockedReasons,
        List<String> lossReasons
) {
}
