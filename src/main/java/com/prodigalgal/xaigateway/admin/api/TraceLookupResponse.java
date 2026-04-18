package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record TraceLookupResponse(
        String requestId,
        String gatewayResourceKey,
        String upstreamObjectId,
        List<RequestLogResponse> matches,
        ObservabilityTraceResponse trace
) {
}
