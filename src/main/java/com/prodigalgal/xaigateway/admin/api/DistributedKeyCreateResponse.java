package com.prodigalgal.xaigateway.admin.api;

public record DistributedKeyCreateResponse(
        DistributedKeyResponse record,
        String fullKey
) {
}
