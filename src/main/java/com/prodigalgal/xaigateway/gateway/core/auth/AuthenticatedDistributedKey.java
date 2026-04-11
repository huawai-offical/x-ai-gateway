package com.prodigalgal.xaigateway.gateway.core.auth;

import java.util.List;

public record AuthenticatedDistributedKey(
        Long id,
        String keyPrefix,
        String keyName,
        List<String> allowedClientFamilies
) {
    public AuthenticatedDistributedKey(Long id, String keyPrefix, String keyName) {
        this(id, keyPrefix, keyName, List.of());
    }
}
