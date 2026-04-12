package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import java.util.List;

public record GatewayChatRuntimeResult(
        String text,
        GatewayUsage usage,
        List<GatewayToolCall> toolCalls
) {
}
