package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import tools.jackson.databind.JsonNode;

public record AdminResourceExecuteResponse(
        RouteSelectionResult routeSelection,
        CanonicalExecutionPlan plan,
        ExecutionBackend executionBackend,
        String upstreamPath,
        String objectMode,
        int statusCode,
        String contentType,
        JsonNode responseJson,
        String responseText,
        Integer binaryLength
) {
}
