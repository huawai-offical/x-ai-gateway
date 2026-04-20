package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestStatus;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;

public record RequestLogResponse(
        Long id,
        String requestId,
        Long distributedKeyId,
        String distributedKeyPrefix,
        String protocol,
        String requestPath,
        String resourceType,
        String operation,
        String requestedModel,
        String publicModel,
        String resolvedModelKey,
        String modelGroup,
        ProviderType providerType,
        Long credentialId,
        String selectionSource,
        String executionBackend,
        String supportStatus,
        String degradationLevel,
        String objectMode,
        String gatewayResourceKey,
        String responseKind,
        String responseObjectType,
        String responseObjectId,
        String responseStatus,
        Integer canonicalEventCount,
        GatewayRequestStatus status,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Long durationMs,
        String errorCode,
        String errorMessage
) {
}
