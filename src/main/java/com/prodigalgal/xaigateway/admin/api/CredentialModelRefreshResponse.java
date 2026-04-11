package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record CredentialModelRefreshResponse(
        Long credentialId,
        int modelCount,
        List<String> sampleModels,
        Instant refreshedAt
) {
}
