package com.prodigalgal.xaigateway.gateway.core.routing;

public record RouteExecutionAttempt(
        int attempt,
        Long credentialId,
        String providerType,
        String outcome,
        String detail
) {
}
