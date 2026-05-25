package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record ObservabilityHealthResponse(
        Instant sampledFrom,
        Instant sampledTo,
        HealthMetricResponse total,
        List<CredentialHealthMetricResponse> credentials,
        List<ProviderHealthMetricResponse> providers
) {
    public ObservabilityHealthResponse {
        credentials = credentials == null ? List.of() : List.copyOf(credentials);
        providers = providers == null ? List.of() : List.copyOf(providers);
    }
}
