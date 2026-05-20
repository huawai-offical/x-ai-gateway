package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FunctionalProviderSmokeResponse(
        Long credentialId,
        String status,
        String classification,
        String skippedReason,
        String baseUrl,
        ProviderType providerType,
        String protocol,
        boolean dryRun,
        boolean liveAllowed,
        boolean routeEligible,
        String routeBlockReason,
        String credentialFingerprint,
        Instant checkedAt,
        String message,
        Map<String, Integer> summary,
        List<FunctionalProviderSmokeItemResponse> items
) {
}
