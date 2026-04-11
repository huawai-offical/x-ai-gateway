package com.prodigalgal.xaigateway.gateway.core.routing;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;

public record RouteSelectionRequest(
        String distributedKeyPrefix,
        String protocol,
        String requestPath,
        String requestedModel,
        Object requestBody,
        GatewayClientFamily clientFamily,
        boolean reserveGovernance
) {
    public RouteSelectionRequest(
            String distributedKeyPrefix,
            String protocol,
            String requestPath,
            String requestedModel,
            Object requestBody) {
        this(distributedKeyPrefix, protocol, requestPath, requestedModel, requestBody, GatewayClientFamily.GENERIC_OPENAI, false);
    }
}
