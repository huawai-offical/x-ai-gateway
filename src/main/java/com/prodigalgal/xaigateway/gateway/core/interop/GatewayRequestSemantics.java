package com.prodigalgal.xaigateway.gateway.core.interop;

import java.util.List;

public record GatewayRequestSemantics(
        TranslationResourceType resourceType,
        TranslationOperation operation,
        String surface,
        String normalizedPath,
        List<InteropFeature> requiredFeatures,
        RouteSelectionMode routeSelectionMode
) {
    public GatewayRequestSemantics(
            TranslationResourceType resourceType,
            TranslationOperation operation,
            String surface,
            String normalizedPath,
            List<InteropFeature> requiredFeatures,
            boolean requiresRouteSelection
    ) {
        this(
                resourceType,
                operation,
                surface,
                normalizedPath,
                requiredFeatures,
                deriveSelectionMode(resourceType, operation, requiresRouteSelection)
        );
    }

    public GatewayRequestSemantics(
            TranslationResourceType resourceType,
            TranslationOperation operation,
            List<InteropFeature> requiredFeatures,
            boolean requiresRouteSelection
    ) {
        this(
                resourceType,
                operation,
                defaultSurface(resourceType, operation),
                defaultNormalizedPath(resourceType, operation),
                requiredFeatures,
                deriveSelectionMode(resourceType, operation, requiresRouteSelection)
        );
    }

    public GatewayRequestSemantics(
            TranslationResourceType resourceType,
            TranslationOperation operation,
            List<InteropFeature> requiredFeatures,
            RouteSelectionMode routeSelectionMode
    ) {
        this(
                resourceType,
                operation,
                defaultSurface(resourceType, operation),
                defaultNormalizedPath(resourceType, operation),
                requiredFeatures,
                routeSelectionMode
        );
    }

    public GatewayRequestSemantics {
        resourceType = resourceType == null ? TranslationResourceType.UNKNOWN : resourceType;
        operation = operation == null ? TranslationOperation.UNKNOWN : operation;
        surface = surface == null || surface.isBlank() ? defaultSurface(resourceType, operation) : surface;
        normalizedPath = normalizedPath == null || normalizedPath.isBlank()
                ? defaultNormalizedPath(resourceType, operation)
                : normalizedPath;
        requiredFeatures = requiredFeatures == null ? List.of() : List.copyOf(requiredFeatures);
        routeSelectionMode = routeSelectionMode == null
                ? defaultSelectionMode(resourceType, operation)
                : routeSelectionMode;
    }

    public boolean requiresRouteSelection() {
        return routeSelectionMode == RouteSelectionMode.CATALOG_SELECTION;
    }

    private static String defaultSurface(TranslationResourceType resourceType, TranslationOperation operation) {
        return ResourceSurfaceRegistry.defaultSurface(resourceType, operation);
    }

    private static String defaultNormalizedPath(TranslationResourceType resourceType, TranslationOperation operation) {
        return ResourceSurfaceRegistry.defaultNormalizedPath(resourceType, operation);
    }

    private static RouteSelectionMode deriveSelectionMode(
            TranslationResourceType resourceType,
            TranslationOperation operation,
            boolean requiresRouteSelection) {
        if (requiresRouteSelection) {
            return RouteSelectionMode.CATALOG_SELECTION;
        }
        return defaultSelectionMode(resourceType, operation);
    }

    private static RouteSelectionMode defaultSelectionMode(
            TranslationResourceType resourceType,
            TranslationOperation operation) {
        return ResourceSurfaceRegistry.defaultRouteSelectionMode(resourceType, operation);
    }
}
