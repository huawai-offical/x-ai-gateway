package com.prodigalgal.xaigateway.gateway.core.auth;

import java.util.List;

public record ResolvedAccessPolicy(
        List<String> sourceAccessGroups,
        List<String> allowedProtocols,
        List<String> allowedModels,
        List<String> allowedProviderTypes,
        List<String> allowedClientFamilies,
        Integer rpmLimit,
        Integer tpmLimit,
        Integer concurrencyLimit,
        Long dailyTokenLimit
) {
    public static ResolvedAccessPolicy empty() {
        return new ResolvedAccessPolicy(List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null, null);
    }
}
