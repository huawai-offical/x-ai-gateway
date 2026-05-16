package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.Map;

public record OfficialCodexResponsesSmokeResponse(
        Long accountId,
        String status,
        String classification,
        String skippedReason,
        String method,
        String path,
        String baseUrl,
        boolean codexAppApi,
        String model,
        boolean dryRun,
        boolean routeEligible,
        String routeBlockReason,
        String credentialFingerprint,
        Integer httpStatus,
        String upstreamRequestId,
        String upstreamResponseId,
        Long durationMs,
        String failureType,
        String failureMessage,
        Map<String, Object> keepalive,
        Instant checkedAt,
        String message,
        Map<String, Object> requestPreview
) {
}
