package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalBalanceLedgerResponse(
        Long id,
        long deltaTokenCredits,
        long balanceAfterTokenCredits,
        String reason,
        String referenceType,
        String referenceId,
        Instant createdAt
) {
}
