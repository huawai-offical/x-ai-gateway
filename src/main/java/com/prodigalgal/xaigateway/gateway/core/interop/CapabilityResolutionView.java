package com.prodigalgal.xaigateway.gateway.core.interop;

import java.util.List;

public record CapabilityResolutionView(
        String declaredLevel,
        String implementedLevel,
        String effectiveLevel,
        String supportStatus,
        String degradationLevel,
        List<String> blockedReasons,
        List<String> lossReasons
) {
    public CapabilityResolutionView(
            String declaredLevel,
            String implementedLevel,
            String effectiveLevel,
            List<String> blockedReasons,
            List<String> lossReasons
    ) {
        this(
                declaredLevel,
                implementedLevel,
                effectiveLevel,
                wireStatus(levelOf(effectiveLevel), blockedReasons),
                wireLevel(SupportStatus.normalizeDegradationLevel(levelOf(effectiveLevel), blockedReasons)),
                blockedReasons,
                lossReasons
        );
    }

    public static CapabilityResolutionView from(CapabilityResolution resolution) {
        return new CapabilityResolutionView(
                wireLevel(resolution == null ? null : resolution.declaredLevel()),
                wireLevel(resolution == null ? null : resolution.implementedLevel()),
                wireLevel(resolution == null ? null : resolution.effectiveLevel()),
                wireStatus(
                        resolution == null ? null : resolution.effectiveLevel(),
                        resolution == null ? List.of() : resolution.blockedReasons()
                ),
                wireLevel(SupportStatus.normalizeDegradationLevel(
                        resolution == null ? null : resolution.effectiveLevel(),
                        resolution == null ? List.of() : resolution.blockedReasons()
                )),
                resolution == null ? List.of() : resolution.blockedReasons(),
                resolution == null ? List.of() : resolution.lossReasons()
        );
    }

    public CapabilityResolutionView {
        blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
        lossReasons = lossReasons == null ? List.of() : List.copyOf(lossReasons);
    }

    private static String wireLevel(InteropCapabilityLevel level) {
        return level == null ? null : level.name().toLowerCase();
    }

    private static String wireStatus(InteropCapabilityLevel level, List<String> blockedReasons) {
        SupportStatus status = SupportStatus.fromLevel(level, blockedReasons == null ? List.of() : blockedReasons);
        return status.wireName();
    }

    private static InteropCapabilityLevel levelOf(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }
        try {
            return InteropCapabilityLevel.valueOf(level.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
