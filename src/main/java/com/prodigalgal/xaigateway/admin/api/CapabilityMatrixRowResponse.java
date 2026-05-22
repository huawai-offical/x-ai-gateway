package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionView;
import com.prodigalgal.xaigateway.gateway.core.catalog.SurfaceCapabilityView;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.SiteProfileSource;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CapabilityMatrixRowResponse(
        Long siteProfileId,
        String profileCode,
        String displayName,
        ProviderFamily providerFamily,
        UpstreamSiteKind siteKind,
        SiteProfileSource profileSource,
        AuthStrategy authStrategy,
        PathStrategy pathStrategy,
        ErrorSchemaStrategy errorSchemaStrategy,
        String healthState,
        String blockedReason,
        List<String> supportedProtocols,
        String compatibilitySurface,
        List<String> credentialRequirements,
        String streamTransport,
        String fallbackStrategy,
        int cooldownCredentialCount,
        Instant cooldownUntil,
        long linkedCredentialCount,
        boolean hasSnapshot,
        int modelCount,
        Instant refreshedAt,
        ExecutionBackend preferredBackend,
        List<ExecutionBackend> supportedBackends,
        Map<String, CapabilityResolutionView> features,
        Map<String, SurfaceCapabilityView> surfaces,
        boolean supportsResponses,
        boolean supportsEmbeddings,
        boolean supportsAudio,
        boolean supportsImages,
        boolean supportsModeration,
        boolean supportsFiles,
        boolean supportsUploads
) {
    public CapabilityMatrixRowResponse(
            Long siteProfileId,
            String profileCode,
            String displayName,
            ProviderFamily providerFamily,
            UpstreamSiteKind siteKind,
            SiteProfileSource profileSource,
            AuthStrategy authStrategy,
            PathStrategy pathStrategy,
            ErrorSchemaStrategy errorSchemaStrategy,
            String healthState,
            String blockedReason,
            List<String> supportedProtocols,
            String compatibilitySurface,
            List<String> credentialRequirements,
            String streamTransport,
            String fallbackStrategy,
            int cooldownCredentialCount,
            Instant cooldownUntil,
            long linkedCredentialCount,
            boolean hasSnapshot,
            int modelCount,
            Instant refreshedAt,
            Map<String, CapabilityResolutionView> features,
            Map<String, SurfaceCapabilityView> surfaces,
            boolean supportsResponses,
            boolean supportsEmbeddings,
            boolean supportsAudio,
            boolean supportsImages,
            boolean supportsModeration,
            boolean supportsFiles,
            boolean supportsUploads
    ) {
        this(
                siteProfileId,
                profileCode,
                displayName,
                providerFamily,
                siteKind,
                profileSource,
                authStrategy,
                pathStrategy,
                errorSchemaStrategy,
                healthState,
                blockedReason,
                supportedProtocols,
                compatibilitySurface,
                credentialRequirements,
                streamTransport,
                fallbackStrategy,
                cooldownCredentialCount,
                cooldownUntil,
                linkedCredentialCount,
                hasSnapshot,
                modelCount,
                refreshedAt,
                ExecutionBackend.SPRING_AI,
                List.of(ExecutionBackend.SPRING_AI),
                features,
                surfaces,
                supportsResponses,
                supportsEmbeddings,
                supportsAudio,
                supportsImages,
                supportsModeration,
                supportsFiles,
                supportsUploads
        );
    }
}
