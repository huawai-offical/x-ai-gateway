package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record RedeemCodeBatchRequest(
        List<String> codes,
        Integer generateCount,
        String prefix,
        Integer maxUses,
        Boolean active,
        Instant expiresAt
) {
}
