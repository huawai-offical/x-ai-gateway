package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import reactor.core.publisher.Flux;

public record ChatExecutionStreamResponse(
        String requestId,
        RouteSelectionResult routeSelection,
        Flux<ChatExecutionStreamChunk> chunks
) {
}
