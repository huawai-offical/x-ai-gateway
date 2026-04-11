package com.prodigalgal.xaigateway.gateway.core.file;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;

public record GatewayFileBindingResponse(
        Long id,
        String fileKey,
        ProviderType providerType,
        Long credentialId,
        String externalFileId,
        String externalFilename,
        String status,
        Instant lastSyncedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
