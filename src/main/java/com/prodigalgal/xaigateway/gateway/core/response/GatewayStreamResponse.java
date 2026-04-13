package com.prodigalgal.xaigateway.gateway.core.response;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import reactor.core.publisher.Flux;

public record GatewayStreamResponse(
        String requestId,
        RouteSelectionResult routeSelection,
        Flux<GatewayStreamEvent> events
) {
}
