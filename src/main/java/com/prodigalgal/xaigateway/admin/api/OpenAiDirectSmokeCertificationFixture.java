package com.prodigalgal.xaigateway.admin.api;

import java.util.Map;

public record OpenAiDirectSmokeCertificationFixture(
        String resourceFamily,
        String status,
        String classification,
        String skippedReason,
        String method,
        String path,
        boolean billable,
        boolean writeOperation,
        Integer httpStatus,
        String upstreamRequestId,
        Long durationMs,
        String failureType,
        String failureMessage,
        Map<String, Object> evidence,
        Map<String, Object> requestPreview
) {
}
