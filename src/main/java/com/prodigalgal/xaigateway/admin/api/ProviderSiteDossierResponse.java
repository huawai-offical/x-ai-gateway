package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record ProviderSiteDossierResponse(
        ProviderSiteResponse site,
        List<SiteModelCapabilityResponse> capabilities,
        List<SurfaceDossierItemResponse> blockedSurfaces,
        List<SurfaceDossierItemResponse> degradedSurfaces,
        List<SurfaceDossierItemResponse> acceptedExceptions,
        List<String> recommendedActions
) {
}
