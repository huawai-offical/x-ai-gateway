package com.prodigalgal.xaigateway.gateway.core.auth;

public record AuthenticatedDistributedKey(
        Long id,
        String keyPrefix,
        String keyName
) {
}
