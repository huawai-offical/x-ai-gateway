package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record SurfaceDossierItemResponse(
        String surfaceKey,
        String operation,
        String normalizedPath,
        String supportStatus,
        String degradationLevel,
        String overallCapabilityLevel,
        List<String> blockerReasons,
        List<String> lossReasons
) {
}
