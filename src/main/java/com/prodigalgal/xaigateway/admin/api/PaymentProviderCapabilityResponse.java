package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record PaymentProviderCapabilityResponse(
        String provider,
        String displayName,
        String checkoutMethod,
        String webhookSignature,
        boolean productionReady,
        List<String> supportedOperations,
        String smokeHint
) {
}
