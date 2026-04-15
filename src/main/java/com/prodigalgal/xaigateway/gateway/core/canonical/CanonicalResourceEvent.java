package com.prodigalgal.xaigateway.gateway.core.canonical;

import java.util.Map;

public record CanonicalResourceEvent(
        String eventType,
        String objectType,
        String objectId,
        String lifecyclePhase,
        String status,
        Map<String, Object> details
) {
    public CanonicalResourceEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
