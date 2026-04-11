package com.prodigalgal.xaigateway.gateway.core.catalog;

public record GatewayPublicModelView(
        String publicModelId,
        String resolvedModelKey,
        boolean alias
) {
}
