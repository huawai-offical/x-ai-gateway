package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record FunctionalProviderSmokeRequest(
        Boolean dryRun,
        Boolean allowLive,
        String protocol,
        String baseUrl,
        Integer timeoutSeconds,
        String model,
        List<String> resourceFamilies,
        Boolean allowBillableProbes
) {
}
