package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.time.Instant;
import java.util.Map;

public record ProviderProtocolEndpointResponse(
        Long id,
        Long siteProfileId,
        String endpointCode,
        String displayName,
        String protocolSuite,
        ProviderType providerType,
        UpstreamSiteKind siteKind,
        String baseUrl,
        AuthStrategy authStrategy,
        PathStrategy pathStrategy,
        ModelAddressingStrategy modelAddressingStrategy,
        ErrorSchemaStrategy errorSchemaStrategy,
        String streamTransport,
        Map<String, Object> conversationProfile,
        boolean active,
        long linkedCredentialCount,
        Instant createdAt,
        Instant updatedAt
) {
}
