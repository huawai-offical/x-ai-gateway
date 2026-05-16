package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record ProviderMediaCapabilityRow(
        String capability,
        String endpointSurface,
        String supportStatus,
        List<String> providerPresets,
        String governanceBoundary,
        String smokeHint
) {
}
