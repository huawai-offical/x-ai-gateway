package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record AccountModelRefreshResponse(
        Long accountId,
        int modelCount,
        List<String> sampleModels,
        Instant refreshedAt
) {
}
