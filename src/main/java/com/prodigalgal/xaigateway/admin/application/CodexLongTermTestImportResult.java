package com.prodigalgal.xaigateway.admin.application;

import java.time.Instant;
import java.util.Map;

public record CodexLongTermTestImportResult(
        Long accountId,
        Long poolId,
        String accountName,
        String externalAccountId,
        String status,
        boolean routeEligible,
        String routeBlockReason,
        String credentialFingerprint,
        Instant importedAt,
        Map<String, Object> safeSummary) {
}
