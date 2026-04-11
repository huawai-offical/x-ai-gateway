package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;

public record CredentialResponse(
        Long id,
        String credentialName,
        ProviderType providerType,
        String baseUrl,
        String apiKeyFingerprint,
        boolean active,
        Instant cooldownUntil,
        String lastErrorCode,
        String lastErrorMessage,
        Instant lastErrorAt,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
