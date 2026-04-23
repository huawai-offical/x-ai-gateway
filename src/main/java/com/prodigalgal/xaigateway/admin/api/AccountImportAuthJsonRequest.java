package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AccountImportAuthJsonRequest(
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
        List<String> supportedModels
) {
}
