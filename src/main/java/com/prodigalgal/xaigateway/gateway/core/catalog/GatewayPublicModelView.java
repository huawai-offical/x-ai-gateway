package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;

public record GatewayPublicModelView(
        String publicModelId,
        String resolvedModelKey,
        boolean alias,
        Long siteProfileId,
        ProviderFamily providerFamily,
        UpstreamSiteKind siteKind,
        InteropCapabilityLevel capabilityLevel,
        boolean supportsChat,
        boolean supportsEmbeddings
) {
    public GatewayPublicModelView(
            String publicModelId,
            String resolvedModelKey,
            boolean alias
    ) {
        this(publicModelId, resolvedModelKey, alias, null, null, null, InteropCapabilityLevel.NATIVE, true, false);
    }
}
