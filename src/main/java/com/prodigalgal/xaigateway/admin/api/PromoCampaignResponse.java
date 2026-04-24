package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record PromoCampaignResponse(
        Long id,
        String campaignName,
        String description,
        boolean active,
        long rewardTokenCredits,
        int maxRedemptionsPerUser,
        Instant startsAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
}
