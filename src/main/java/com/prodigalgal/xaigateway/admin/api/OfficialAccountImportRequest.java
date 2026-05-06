package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record OfficialAccountImportRequest(
        @NotNull OfficialAccountType accountType,
        Long poolId,
        String accountName,
        String externalAccountId,
        @NotBlank String accessToken,
        String refreshToken,
        String metadataJson,
        Boolean active,
        Long proxyId,
        Long tlsFingerprintProfileId,
        Long siteProfileId,
        List<String> supportedModels,
        Instant tokenExpiresAt,
        String planTier,
        String subscriptionTier,
        Integer quotaWindowSeconds,
        Long quotaRemainingTokens,
        Long quotaRemainingRequests,
        Instant quotaResetAt,
        Boolean refreshQuotaAfterImport
) {
}
