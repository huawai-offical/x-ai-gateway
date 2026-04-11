package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.util.List;

public record CredentialConnectivityResponse(
        ProviderType providerType,
        String baseUrl,
        boolean reachable,
        long latencyMs,
        int discoveredModelCount,
        List<String> sampleModels,
        String message
) {
}
