package com.prodigalgal.xaigateway.gateway.core.interop;

import java.util.List;

public record GatewayRequestSemantics(
        TranslationResourceType resourceType,
        TranslationOperation operation,
        List<InteropFeature> requiredFeatures,
        boolean requiresRouteSelection
) {
}
