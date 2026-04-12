package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;

public record GatewayResourceExecutionContext(
        RouteSelectionResult selectionResult,
        UpstreamCredentialEntity credential,
        String apiKey,
        String requestPath
) {
}
