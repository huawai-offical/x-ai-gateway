package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.Map;

public record CredentialResponse(
        Long id,
        String credentialName,
        ProviderType providerType,
        String baseUrl,
        CredentialAuthKind authKind,
        String secretFingerprint,
        Map<String, Object> credentialMetadata,
        boolean active,
        Instant cooldownUntil,
        String lastErrorCode,
        String lastErrorMessage,
        Instant lastErrorAt,
        Instant lastUsedAt,
        Long proxyId,
        Long tlsFingerprintProfileId,
        Long siteProfileId,
        Instant createdAt,
        Instant updatedAt
) {
}
