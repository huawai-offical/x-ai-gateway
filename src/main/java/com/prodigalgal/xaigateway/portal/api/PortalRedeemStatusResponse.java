package com.prodigalgal.xaigateway.portal.api;

public record PortalRedeemStatusResponse(
        boolean available,
        String message,
        long currentTokenCredits
) {
}
