package com.prodigalgal.xaigateway.admin.api;

public record CodexRuntimeBatchRecoveryItemResponse(
        Long accountId,
        String accountName,
        String category,
        String status,
        String reason,
        String recommendedAction,
        String errorSummary,
        String executionStatus,
        String executionError
) {
}
