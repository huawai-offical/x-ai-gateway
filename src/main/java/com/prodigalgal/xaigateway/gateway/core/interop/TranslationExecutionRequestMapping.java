package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import java.util.List;
import java.util.Map;

public record TranslationExecutionRequestMapping(
        String protocol,
        String requestPath,
        String requestedModel,
        String publicModel,
        String resolvedModelKey,
        GatewayClientFamily clientFamily,
        List<InteropFeature> requiredFeatures,
        Map<String, InteropCapabilityLevel> featureLevels
) {
    public static TranslationExecutionRequestMapping fromLegacy(Map<String, Object> legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return new TranslationExecutionRequestMapping(null, null, null, null, null, null, List.of(), Map.of());
        }
        return new TranslationExecutionRequestMapping(
                legacy.get("protocol") instanceof String value ? value : null,
                legacy.get("requestPath") instanceof String value ? value : null,
                legacy.get("requestedModel") instanceof String value ? value : null,
                legacy.get("publicModel") instanceof String value ? value : null,
                legacy.get("resolvedModelKey") instanceof String value ? value : null,
                null,
                List.of(),
                Map.of()
        );
    }
}
