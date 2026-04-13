package com.prodigalgal.xaigateway.gateway.core.interop;

import java.util.List;

public record CapabilityResolutionView(
        String declaredLevel,
        String implementedLevel,
        String effectiveLevel,
        List<String> blockedReasons,
        List<String> lossReasons
) {
    public static CapabilityResolutionView from(CapabilityResolution resolution) {
        return new CapabilityResolutionView(
                wireLevel(resolution == null ? null : resolution.declaredLevel()),
                wireLevel(resolution == null ? null : resolution.implementedLevel()),
                wireLevel(resolution == null ? null : resolution.effectiveLevel()),
                resolution == null ? List.of() : resolution.blockedReasons(),
                resolution == null ? List.of() : resolution.lossReasons()
        );
    }

    private static String wireLevel(InteropCapabilityLevel level) {
        return level == null ? null : level.name().toLowerCase();
    }
}
