package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record RedeemCodeUpdateRequest(
        Boolean active,
        Integer maxUses,
        Instant expiresAt
) {
}
