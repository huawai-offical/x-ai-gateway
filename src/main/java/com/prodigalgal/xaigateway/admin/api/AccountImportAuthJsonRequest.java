package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AccountImportAuthJsonRequest(
        @NotNull Long poolId,
        String accountName,
        String externalAccountId,
        @NotBlank String accessToken,
        String refreshToken,
        String metadataJson,
        Boolean active,
        Long proxyId,
        Long tlsFingerprintProfileId,
        Long siteProfileId
) {
}

