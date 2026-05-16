package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record ProviderReferenceGapResponse(
        String referenceName,
        String referenceVersion,
        String catalogVersion,
        String catalogSource,
        Instant generatedAt,
        List<ProviderReferenceGapRow> providers,
        List<ProviderMediaCapabilityRow> mediaCapabilities,
        List<ProviderPricingSyncStatusRow> pricingSync,
        List<String> recommendedActions
) {
}
