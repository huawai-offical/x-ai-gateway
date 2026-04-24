package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record RedeemCodeResponse(
        Long id,
        Long campaignId,
        String campaignName,
        String code,
        boolean active,
        int maxUses,
        int usedCount,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
}
