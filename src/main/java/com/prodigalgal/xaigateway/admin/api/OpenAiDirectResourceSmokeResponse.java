package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OpenAiDirectResourceSmokeResponse(
        Long credentialId,
        String status,
        String classification,
        String skippedReason,
        String baseUrl,
        ProviderType providerType,
        boolean dryRun,
        boolean routeEligible,
        String routeBlockReason,
        String credentialFingerprint,
        Instant checkedAt,
        String message,
        Map<String, Integer> summary,
        List<OpenAiDirectResourceSmokeItemResponse> items
) {
}
