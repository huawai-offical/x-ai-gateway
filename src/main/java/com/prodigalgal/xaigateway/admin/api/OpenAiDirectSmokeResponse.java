package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OpenAiDirectSmokeResponse(
        Long credentialId,
        String status,
        String classification,
        String skippedReason,
        String method,
        String path,
        String baseUrl,
        ProviderType providerType,
        boolean dryRun,
        boolean routeEligible,
        String routeBlockReason,
        String credentialFingerprint,
        Integer httpStatus,
        String upstreamRequestId,
        Long durationMs,
        String failureType,
        String failureMessage,
        Integer modelsCount,
        List<String> sampleModels,
        Instant checkedAt,
        String message,
        Map<String, Object> requestPreview
) {
}
