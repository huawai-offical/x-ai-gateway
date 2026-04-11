package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GatewayFileImportRequest(
        @NotNull(message = "distributedKeyId 不能为空。")
        Long distributedKeyId,
        @NotNull(message = "providerType 不能为空。")
        ProviderType providerType,
        @NotNull(message = "credentialId 不能为空。")
        Long credentialId,
        @NotBlank(message = "externalFileId 不能为空。")
        String externalFileId,
        String externalFilename,
        String mimeType,
        String purpose
) {
}
