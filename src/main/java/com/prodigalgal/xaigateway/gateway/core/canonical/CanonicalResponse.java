package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import java.util.List;

public record CanonicalResponse(
        String requestId,
        String publicModel,
        String outputText,
        String reasoning,
        List<CanonicalToolCall> toolCalls,
        CanonicalUsage usage,
        GatewayFinishReason finishReason
) {
}
