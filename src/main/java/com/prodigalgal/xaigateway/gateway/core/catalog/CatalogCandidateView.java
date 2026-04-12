package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.util.List;

public record CatalogCandidateView(
        Long credentialId,
        String credentialName,
        ProviderType providerType,
        Long siteProfileId,
        ProviderFamily providerFamily,
        UpstreamSiteKind siteKind,
        AuthStrategy authStrategy,
        PathStrategy pathStrategy,
        ErrorSchemaStrategy errorSchemaStrategy,
        String baseUrl,
        String modelName,
        String modelKey,
        List<String> supportedProtocols,
        boolean supportsChat,
        boolean supportsEmbeddings,
        boolean supportsCache,
        boolean supportsThinking,
        boolean supportsVisibleReasoning,
        boolean supportsReasoningReuse,
        ReasoningTransport reasoningTransport,
        InteropCapabilityLevel capabilityLevel
) {
    public CatalogCandidateView(
            Long credentialId,
            String credentialName,
            ProviderType providerType,
            String baseUrl,
            String modelName,
            String modelKey,
            List<String> supportedProtocols,
            boolean supportsChat,
            boolean supportsEmbeddings,
            boolean supportsCache,
            boolean supportsThinking,
            boolean supportsVisibleReasoning,
            boolean supportsReasoningReuse,
            ReasoningTransport reasoningTransport
    ) {
        this(
                credentialId,
                credentialName,
                providerType,
                null,
                null,
                null,
                null,
                null,
                null,
                baseUrl,
                modelName,
                modelKey,
                supportedProtocols,
                supportsChat,
                supportsEmbeddings,
                supportsCache,
                supportsThinking,
                supportsVisibleReasoning,
                supportsReasoningReuse,
                reasoningTransport,
                InteropCapabilityLevel.NATIVE
        );
    }
}
