package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.util.List;

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
}
