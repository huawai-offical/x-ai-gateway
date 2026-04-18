package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ObservabilityTraceResponse;
import com.prodigalgal.xaigateway.admin.api.RequestLogResponse;
import com.prodigalgal.xaigateway.admin.api.TraceLookupResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TraceWorkbenchService {

    private final ObservabilityQueryService observabilityQueryService;

    public TraceWorkbenchService(ObservabilityQueryService observabilityQueryService) {
        this.observabilityQueryService = observabilityQueryService;
    }

    public TraceLookupResponse lookup(String requestId, String gatewayResourceKey, String upstreamObjectId) {
        List<RequestLogResponse> matches = observabilityQueryService.listRequestLogs(
                null,
                null,
                null,
                null,
                normalize(requestId),
                normalize(gatewayResourceKey),
                normalize(upstreamObjectId)
        );
        String resolvedRequestId = normalize(requestId);
        if (resolvedRequestId == null && !matches.isEmpty()) {
            resolvedRequestId = matches.getFirst().requestId();
        }
        ObservabilityTraceResponse trace = resolvedRequestId == null
                ? null
                : observabilityQueryService.trace(resolvedRequestId);
        String resolvedGatewayResourceKey = normalize(gatewayResourceKey);
        if (resolvedGatewayResourceKey == null && trace != null && trace.requestLog() != null) {
            resolvedGatewayResourceKey = trace.requestLog().gatewayResourceKey();
        }
        String resolvedUpstreamObjectId = normalize(upstreamObjectId);
        if (resolvedUpstreamObjectId == null && trace != null && trace.asyncResourceSummary() != null) {
            resolvedUpstreamObjectId = trace.asyncResourceSummary().upstreamObjectId();
        }
        return new TraceLookupResponse(
                resolvedRequestId,
                resolvedGatewayResourceKey,
                resolvedUpstreamObjectId,
                matches,
                trace
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
