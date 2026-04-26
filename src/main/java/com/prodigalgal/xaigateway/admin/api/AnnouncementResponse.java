package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record AnnouncementResponse(
        Long id,
        String title,
        String summary,
        String body,
        String status,
        String audienceType,
        Long audienceUserId,
        String audienceUserEmail,
        Long audiencePlanId,
        String audiencePlanName,
        Long audienceAccessGroupId,
        String audienceAccessGroupName,
        Instant publishedAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
}
