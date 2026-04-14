package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import reactor.core.publisher.Flux;

public record CanonicalExecutionStreamResult(
        String requestId,
        RouteSelectionResult routeSelection,
        Flux<CanonicalStreamEvent> events
) {
}
