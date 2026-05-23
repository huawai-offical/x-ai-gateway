package com.prodigalgal.xaigateway.gateway.core.routing;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;

public record RouteSelectionRequest(
        String distributedKeyPrefix,
        String protocol,
        String requestPath,
        String requestedModel,
        Object requestBody,
        GatewayClientFamily clientFamily,
        boolean reserveGovernance,
        String sessionAffinityKey,
        String httpMethod
) {
    public RouteSelectionRequest(
            String distributedKeyPrefix,
            String protocol,
            String requestPath,
            String requestedModel,
            Object requestBody) {
        this(distributedKeyPrefix, protocol, requestPath, requestedModel, requestBody, GatewayClientFamily.GENERIC_OPENAI, false, null);
    }

    public RouteSelectionRequest(
            String distributedKeyPrefix,
            String protocol,
            String requestPath,
            String requestedModel,
            Object requestBody,
            GatewayClientFamily clientFamily,
            boolean reserveGovernance) {
        this(distributedKeyPrefix, protocol, requestPath, requestedModel, requestBody, clientFamily, reserveGovernance, null);
    }

    public RouteSelectionRequest {
        httpMethod = httpMethod == null || httpMethod.isBlank() ? "POST" : httpMethod.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public RouteSelectionRequest(
            String distributedKeyPrefix,
            String protocol,
            String requestPath,
            String requestedModel,
            Object requestBody,
            GatewayClientFamily clientFamily,
            boolean reserveGovernance,
            String sessionAffinityKey) {
        this(distributedKeyPrefix, protocol, requestPath, requestedModel, requestBody, clientFamily, reserveGovernance, sessionAffinityKey, "POST");
    }
}
