package com.prodigalgal.xaigateway.gateway.core.interop;

import java.util.Collection;
import java.util.List;

public record ResourceSurfaceDefinition(
        String key,
        String httpMethod,
        String normalizedPath,
        String surface,
        String protocol,
        TranslationResourceType resourceType,
        TranslationOperation operation,
        List<InteropFeature> requiredFeatures,
        RouteSelectionMode routeSelectionMode,
        String defaultModel,
        boolean providerSurface
) {
    public ResourceSurfaceDefinition {
        resourceType = resourceType == null ? TranslationResourceType.UNKNOWN : resourceType;
        operation = operation == null ? TranslationOperation.UNKNOWN : operation;
        key = key == null || key.isBlank() ? operation.wireName() : key.trim();
        httpMethod = httpMethod == null || httpMethod.isBlank() ? "POST" : httpMethod.trim().toUpperCase();
        normalizedPath = normalizedPath == null || normalizedPath.isBlank() ? null : normalizedPath.trim();
        surface = surface == null || surface.isBlank() ? resourceType.wireName() : surface.trim();
        protocol = protocol == null || protocol.isBlank() ? "openai" : protocol.trim();
        requiredFeatures = requiredFeatures == null ? List.of() : List.copyOf(requiredFeatures);
        routeSelectionMode = routeSelectionMode == null ? RouteSelectionMode.CATALOG_SELECTION : routeSelectionMode;
        defaultModel = defaultModel == null || defaultModel.isBlank() ? null : defaultModel.trim();
    }

    public GatewayRequestSemantics toSemantics() {
        return toSemantics(requiredFeatures);
    }

    public GatewayRequestSemantics toSemantics(Collection<InteropFeature> features) {
        return new GatewayRequestSemantics(
                resourceType,
                operation,
                surface,
                normalizedPath,
                features == null ? List.of() : List.copyOf(features),
                routeSelectionMode
        );
    }
}
