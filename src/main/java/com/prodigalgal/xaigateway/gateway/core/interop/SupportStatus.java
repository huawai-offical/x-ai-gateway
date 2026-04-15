package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import java.util.List;
import java.util.Locale;

public enum SupportStatus {
    NATIVE,
    PASSTHROUGH,
    ORCHESTRATION,
    DEGRADED,
    BLOCKED;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static InteropCapabilityLevel normalizeDegradationLevel(
            InteropCapabilityLevel effectiveLevel,
            List<String> blockerReasons) {
        if (blockerReasons != null && !blockerReasons.isEmpty()) {
            return InteropCapabilityLevel.UNSUPPORTED;
        }
        return effectiveLevel == null ? InteropCapabilityLevel.UNSUPPORTED : effectiveLevel;
    }

    public static SupportStatus fromLevel(
            InteropCapabilityLevel effectiveLevel,
            List<String> blockerReasons) {
        return resolve(null, effectiveLevel, blockerReasons);
    }

    public static SupportStatus resolve(
            ExecutionBackend executionBackend,
            InteropCapabilityLevel effectiveLevel,
            List<String> blockerReasons) {
        InteropCapabilityLevel degradationLevel = normalizeDegradationLevel(effectiveLevel, blockerReasons);
        if (degradationLevel == InteropCapabilityLevel.UNSUPPORTED) {
            return BLOCKED;
        }
        if (degradationLevel != InteropCapabilityLevel.NATIVE) {
            return DEGRADED;
        }
        if (executionBackend == null) {
            return NATIVE;
        }
        return switch (executionBackend) {
            case PASSTHROUGH -> PASSTHROUGH;
            case ORCHESTRATION -> ORCHESTRATION;
            case NATIVE, SPRING_AI -> NATIVE;
        };
    }
}
