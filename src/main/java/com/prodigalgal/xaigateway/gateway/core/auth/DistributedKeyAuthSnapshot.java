package com.prodigalgal.xaigateway.gateway.core.auth;

import java.util.List;

public record DistributedKeyAuthSnapshot(
        Long distributedKeyId,
        String keyPrefix,
        String keyName,
        String maskedKey,
        String secretHash,
        List<String> allowedClientFamilies
) {
}
