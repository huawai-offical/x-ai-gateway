package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record RunbookLinkResponse(
        Long id,
        String linkName,
        String eventType,
        String entityType,
        String linkUrl,
        String description,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
