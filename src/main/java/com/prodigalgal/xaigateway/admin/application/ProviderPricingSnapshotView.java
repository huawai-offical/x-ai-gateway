package com.prodigalgal.xaigateway.admin.application;

import java.time.Instant;

public record ProviderPricingSnapshotView(
        String providerCode,
        String displayName,
        String sourceKind,
        String sourceRef,
        String pricingMetadata,
        String costProfile,
        String snapshotVersion,
        String checksum,
        String approvalStatus,
        String syncStatus,
        Instant lastVerifiedAt,
        Instant effectiveAt,
        Instant supersededAt,
        String driftStatus,
        boolean productionEligible,
        String notes
) {
}
