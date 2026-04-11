package com.prodigalgal.xaigateway.gateway.core.auth;

public record DistributedKeySecrets(
        String keyPrefix,
        String fullKey,
        String secretHash,
        String maskedKey
) {
}
