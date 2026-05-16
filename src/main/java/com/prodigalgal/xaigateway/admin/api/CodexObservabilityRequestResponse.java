package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;

public record CodexObservabilityRequestResponse(
        String requestId,
        Long distributedKeyId,
        String distributedKeyPrefix,
        String clientFamily,
        String clientInstance,
        String workspaceHint,
        String sessionAffinitySource,
        String sessionAffinityKey,
        String model,
        String status,
        ProviderType providerType,
        Long credentialId,
        String routeSummary,
        Integer candidateCount,
        String supportStatus,
        String degradationLevel,
        String filterSummary,
        String filterSummaryJson,
        Integer usageInputTokens,
        Integer usageOutputTokens,
        Integer usageReasoningTokens,
        Integer usageTotalTokens,
        Integer cacheHitTokens,
        Integer cacheWriteTokens,
        Integer savedInputTokens,
        String cacheSummary,
        String errorSummary,
        String diagnosticJson,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Long durationMs
) {
}
