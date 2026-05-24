package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.util.List;

public record PublicDocsProviderPresetResponse(
        String code,
        String displayName,
        UpstreamSiteKind siteKind,
        String compatibilitySurface,
        String supportStrategy,
        List<String> capabilityTags,
        List<String> modelFamilies,
        String pricingMetadata,
        List<String> unsupportedFeatures,
        Object nativeAdapterContract
) {
}
