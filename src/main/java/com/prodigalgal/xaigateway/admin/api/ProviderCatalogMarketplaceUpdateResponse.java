package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record ProviderCatalogMarketplaceUpdateResponse(
        String status,
        String catalogVersion,
        String source,
        String signatureStatus,
        String catalogHash,
        int presetCount,
        boolean cacheWritten,
        boolean previousAvailable,
        Instant updatedAt,
        String message
) {
}
