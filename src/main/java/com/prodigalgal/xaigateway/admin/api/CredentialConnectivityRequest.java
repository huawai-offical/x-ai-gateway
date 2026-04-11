package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CredentialConnectivityRequest(
        @NotNull(message = "providerType 不能为空。")
        ProviderType providerType,
        @NotBlank(message = "baseUrl 不能为空。")
        String baseUrl,
        @NotBlank(message = "apiKey 不能为空。")
        String apiKey
) {
}
