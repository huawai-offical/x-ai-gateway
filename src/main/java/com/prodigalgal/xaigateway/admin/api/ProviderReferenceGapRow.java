package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record ProviderReferenceGapRow(
        String referenceChannel,
        String catalogPresetCode,
        String displayName,
        String supportStatus,
        String supportMode,
        String currentSurface,
        String adapterBoundary,
        List<String> capabilityTags,
        List<String> missingFeatures,
        String notes
) {
}
