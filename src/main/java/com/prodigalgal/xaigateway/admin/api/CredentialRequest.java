package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CredentialRequest(
        @NotBlank(message = "凭证名称不能为空。")
        String credentialName,
        @NotNull(message = "providerType 不能为空。")
        ProviderType providerType,
        @NotBlank(message = "baseUrl 不能为空。")
        String baseUrl,
        @NotBlank(message = "apiKey 不能为空。")
        String apiKey,
        Boolean active,
        Long proxyId,
        Long tlsFingerprintProfileId
) {
}
