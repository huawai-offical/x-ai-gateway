package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceResponse;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record AdminResourceExecuteResponse(
        RouteSelectionResult routeSelection,
        CanonicalExecutionPlan plan,
        ExecutionBackend executionBackend,
        String upstreamPath,
        String objectMode,
        SupportStatus supportStatus,
        InteropCapabilityLevel degradationLevel,
        List<String> blockerReasons,
        int statusCode,
        String contentType,
        JsonNode responseJson,
        String responseText,
        Integer binaryLength,
        CanonicalResourceResponse canonicalResponse
) {
}
