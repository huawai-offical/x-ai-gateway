package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record CodexRuntimeBatchRecoveryRequest(
        Boolean execute,
        Boolean refreshQuota,
        List<Long> accountIds,
        String reason
) {
}
