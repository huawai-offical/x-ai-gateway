package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.catalog.SurfaceCapabilityView;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SiteModelCapabilityResponse(
        Long id,
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
        ReasoningTransport reasoningTransport,
        InteropCapabilityLevel capabilityLevel,
        Map<String, SurfaceCapabilityView> surfaces,
        Instant sourceRefreshedAt
) {
}
