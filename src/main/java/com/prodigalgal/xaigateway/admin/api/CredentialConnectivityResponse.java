package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.List;

public record CredentialConnectivityResponse(
        Long credentialId,
        ProviderType providerType,
        String baseUrl,
        boolean reachable,
        String status,
        long latencyMs,
        int discoveredModelCount,
        List<String> sampleModels,
        String model,
        String upstreamRequestId,
        String responseSummary,
        String errorMessage,
        Instant testedAt,
        String message
) {
}
