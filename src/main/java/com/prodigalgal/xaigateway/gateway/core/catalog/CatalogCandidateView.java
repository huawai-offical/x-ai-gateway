package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import java.util.List;

public record CatalogCandidateView(
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
}
