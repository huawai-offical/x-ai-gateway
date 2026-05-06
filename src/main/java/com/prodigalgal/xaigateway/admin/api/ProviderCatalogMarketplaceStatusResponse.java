package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record ProviderCatalogMarketplaceStatusResponse(
        String activeVersion,
        String activeSource,
        String signatureStatus,
        String catalogHash,
        int presetCount,
        boolean cached,
        boolean previousAvailable,
        Instant updatedAt,
        String message
) {
}
