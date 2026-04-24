package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalRedeemResponse(
        boolean success,
        String message,
        String campaignName,
        long deltaTokenCredits,
        long balanceAfterTokenCredits,
        Instant redeemedAt
) {
}
