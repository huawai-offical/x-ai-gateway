package com.prodigalgal.xaigateway.portal.api;

import java.util.List;

public record PortalSelfServiceSummaryResponse(
        PortalProfileResponse profile,
        long balanceAfterTokenCredits,
        List<PortalKeyResponse> keys,
        List<PortalSubscriptionResponse> subscriptions,
        PortalUsageSummaryResponse usage,
        List<PortalPaymentOrderResponse> recentOrders,
        List<PortalChannelStatusResponse> channels
) {
}
