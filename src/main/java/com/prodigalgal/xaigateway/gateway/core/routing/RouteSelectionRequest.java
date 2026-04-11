package com.prodigalgal.xaigateway.gateway.core.routing;

public record RouteSelectionRequest(
        String distributedKeyPrefix,
        String protocol,
        String requestPath,
        String requestedModel,
        Object requestBody
) {
}
