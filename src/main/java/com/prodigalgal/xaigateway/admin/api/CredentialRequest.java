package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record CredentialRequest(
        @NotBlank(message = "凭证名称不能为空。")
        String credentialName,
        @NotNull(message = "providerType 不能为空。")
        ProviderType providerType,
        @NotBlank(message = "baseUrl 不能为空。")
        String baseUrl,
        CredentialAuthKind authKind,
        String secret,
        String apiKey,
        Map<String, Object> credentialMetadata,
        Boolean active,
        Long proxyId,
        Long tlsFingerprintProfileId,
        Long siteProfileId
) {
    public String resolvedSecret() {
        if (secret != null && !secret.isBlank()) {
            return secret.trim();
        }
        return apiKey == null ? null : apiKey.trim();
    }

    public CredentialAuthKind resolvedAuthKind() {
        return CredentialAuthKind.defaultValue(authKind);
    }

    public Map<String, Object> resolvedCredentialMetadata() {
        return credentialMetadata == null ? Map.of() : Map.copyOf(credentialMetadata);
    }
}
