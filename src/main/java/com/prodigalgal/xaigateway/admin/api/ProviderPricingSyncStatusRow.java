package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record ProviderPricingSyncStatusRow(
        String providerCode,
        String displayName,
        String pricingSource,
        String syncStrategy,
        String syncStatus,
        Instant lastVerifiedAt,
        String snapshotVersion,
        String checksum,
        String sourceKind,
        String approvalStatus,
        Instant effectiveAt,
        Instant supersededAt,
        String driftStatus,
        boolean productionEligible,
        String smokeClassification,
        List<String> failureClasses,
        boolean requiresRealKey,
        String notes
) {
}
