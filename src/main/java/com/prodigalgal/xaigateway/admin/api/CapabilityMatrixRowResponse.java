package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionView;
import com.prodigalgal.xaigateway.gateway.core.catalog.SurfaceCapabilityView;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
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
        boolean supportsUploads,
        boolean supportsBatches,
        boolean supportsTuning,
        boolean supportsRealtime
) {
    public CapabilityMatrixRowResponse(
            Long siteProfileId,
            String profileCode,
            String displayName,
            ProviderFamily providerFamily,
            UpstreamSiteKind siteKind,
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
            Map<String, CapabilityResolutionView> features,
            Map<String, SurfaceCapabilityView> surfaces,
            boolean supportsResponses,
            boolean supportsEmbeddings,
            boolean supportsAudio,
            boolean supportsImages,
            boolean supportsModeration,
            boolean supportsFiles,
            boolean supportsUploads,
            boolean supportsBatches,
            boolean supportsTuning,
            boolean supportsRealtime
    ) {
        this(
                siteProfileId,
                profileCode,
                displayName,
                providerFamily,
                siteKind,
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
                supportsUploads,
                supportsBatches,
                supportsTuning,
                supportsRealtime
        );
    }
}
