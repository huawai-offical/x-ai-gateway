package com.prodigalgal.xaigateway.gateway.core.interop;

public enum GatewayDegradationPolicy {
    STRICT,
    ALLOW_EMULATED,
    ALLOW_LOSSY;

    public boolean allows(InteropCapabilityLevel level) {
        return switch (this) {
            case STRICT -> level == InteropCapabilityLevel.NATIVE;
            case ALLOW_EMULATED -> level == InteropCapabilityLevel.NATIVE || level == InteropCapabilityLevel.EMULATED;
            case ALLOW_LOSSY -> level != InteropCapabilityLevel.UNSUPPORTED;
        };
    }

    public static GatewayDegradationPolicy from(String raw) {
        if (raw == null || raw.isBlank()) {
            return STRICT;
        }
        return GatewayDegradationPolicy.valueOf(raw.trim().toUpperCase());
    }
}
