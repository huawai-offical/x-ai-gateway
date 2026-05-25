package com.prodigalgal.xaigateway.gateway.core.interop;

public enum GatewayDegradationPolicy {
    STRICT,
    ALLOW_EMULATED,
    ALLOW_LOSSY;

    public boolean allows(InteropCapabilityLevel level) {
        return level == InteropCapabilityLevel.NATIVE;
    }

    public static GatewayDegradationPolicy from(String raw) {
        if (raw == null || raw.isBlank()) {
            return STRICT;
        }
        return GatewayDegradationPolicy.valueOf(raw.trim().toUpperCase());
    }
}
