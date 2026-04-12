package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import java.util.List;

public record DiscoveredModelDefinition(
        String modelName,
        String modelKey,
        List<String> supportedProtocols,
        boolean supportsChat,
        boolean supportsTools,
        boolean supportsImageInput,
        boolean supportsEmbeddings,
        boolean supportsCache,
        boolean supportsThinking,
        boolean supportsVisibleReasoning,
        boolean supportsReasoningReuse,
        ReasoningTransport reasoningTransport
) {
}
