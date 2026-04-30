package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record DistributedKeySecretExportGrantResponse(
        Long distributedKeyId,
        String keyName,
        String maskedKey,
        String grantToken,
        Instant expiresAt,
        boolean consumed,
        boolean revoked,
        String warning
) {
}
