package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.util.List;

public record ProviderSitePresetResponse(
        String code,
        String profileCode,
        String displayName,
        String vendorCode,
        String vendorName,
        UpstreamSiteKind siteKind,
        ProviderFamily providerFamily,
        AuthStrategy authStrategy,
        PathStrategy pathStrategy,
        ModelAddressingStrategy modelAddressingStrategy,
        ErrorSchemaStrategy errorSchemaStrategy,
        String defaultBaseUrl,
        String description,
        List<String> supportedProtocols,
        String streamTransport,
        String fallbackStrategy,
        List<String> capabilityTags,
        String costProfile,
        String errorMode,
        String catalogVersion,
        String catalogSource,
        boolean deprecated,
        List<String> conformanceChecks,
        String compatibilitySurface,
        String supportStrategy,
        List<String> modelFamilies,
        String pricingMetadata,
        List<String> unsupportedFeatures,
        Object conversationProfile,
        Object modelPolicies,
        List<ProviderProtocolEndpointResponse> protocolEndpoints,
        boolean imported,
        Long existingSiteProfileId
) {
}
