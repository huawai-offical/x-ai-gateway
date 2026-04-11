package com.prodigalgal.xaigateway.gateway.core.auth;

import java.util.List;

public record DistributedKeyView(
        Long id,
        String keyName,
        String keyPrefix,
        String maskedKey,
        List<String> allowedProtocols,
        List<String> allowedModels,
        List<String> allowedProviderTypes,
        java.time.Instant expiresAt,
        Long budgetLimitMicros,
        Integer budgetWindowSeconds,
        Integer rpmLimit,
        Integer tpmLimit,
        Integer concurrencyLimit,
        Integer stickySessionTtlSeconds,
        List<String> allowedClientFamilies,
        boolean requireClientFamilyMatch,
        List<DistributedCredentialBindingView> bindings
) {
    public DistributedKeyView(
            Long id,
            String keyName,
            String keyPrefix,
            String maskedKey,
            List<String> allowedProtocols,
            List<String> allowedModels,
            List<DistributedCredentialBindingView> bindings) {
        this(
                id,
                keyName,
                keyPrefix,
                maskedKey,
                allowedProtocols,
                allowedModels,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                false,
                bindings
        );
    }
}
