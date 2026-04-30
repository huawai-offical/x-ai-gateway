package com.prodigalgal.xaigateway.admin.api;

public record DistributedKeyCreateResponse(
        DistributedKeyResponse record,
        String fullKey,
        String oneTimeExportToken,
        java.time.Instant oneTimeExportExpiresAt
) {
    public DistributedKeyCreateResponse(DistributedKeyResponse record, String fullKey) {
        this(record, fullKey, null, null);
    }
}
