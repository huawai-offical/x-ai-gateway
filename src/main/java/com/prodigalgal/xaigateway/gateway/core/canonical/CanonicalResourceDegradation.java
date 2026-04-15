package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;

public record CanonicalResourceDegradation(
        String code,
        String message,
        InteropCapabilityLevel level,
        boolean blocker
) {
}
