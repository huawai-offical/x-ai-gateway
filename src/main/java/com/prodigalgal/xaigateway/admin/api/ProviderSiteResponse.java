package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionView;
import com.prodigalgal.xaigateway.gateway.core.catalog.SurfaceCapabilityView;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProviderSiteResponse(
        Long id,
        String profileCode,
        String displayName,
        ProviderFamily providerFamily,
        UpstreamSiteKind siteKind,
        AuthStrategy authStrategy,
        PathStrategy pathStrategy,
        ModelAddressingStrategy modelAddressingStrategy,
        ErrorSchemaStrategy errorSchemaStrategy,
        String baseUrlPattern,
        String description,
        boolean active,
        String healthState,
        String blockedReason,
        List<String> supportedProtocols,
        String compatibilitySurface,
        List<String> credentialRequirements,
        String streamTransport,
        String fallbackStrategy,
        int cooldownCredentialCount,
        Instant cooldownUntil,
        Map<String, CapabilityResolutionView> features,
        Map<String, SurfaceCapabilityView> surfaces,
        int modelCount,
        Instant refreshedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
