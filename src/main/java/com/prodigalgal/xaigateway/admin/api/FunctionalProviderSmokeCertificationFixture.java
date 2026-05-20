package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.util.Map;

public record FunctionalProviderSmokeCertificationFixture(
        ProviderType providerType,
        String protocol,
        String resourceFamily,
        String status,
        String classification,
        String skippedReason,
        String method,
        String path,
        String model,
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
