package com.prodigalgal.xaigateway.admin.api;

public record ExecutionPreviewBindingSummaryResponse(
        Long bindingId,
        int bindingPriority,
        int bindingWeight,
        String capabilityLevel,
        Long siteProfileId,
        Long credentialId,
        String providerType,
        String providerFamily,
        String siteKind,
        String baseUrl,
        String modelKey
) {
}
