package com.prodigalgal.xaigateway.admin.api;

public record LiveSessionCreateRequest(
        Long distributedKeyId,
        String model,
        String protocol,
        String metadataJson,
        Long ttlSeconds
) {
}
