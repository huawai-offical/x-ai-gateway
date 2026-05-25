package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record InvitationCodeResponse(
        Long id,
        String code,
        boolean active,
        int maxUses,
        int usedCount,
        Instant expiresAt,
        Long ownerUserId,
        String ownerEmail,
        String ownerDisplayName,
        long rewardTokenCredits,
        long referrerRewardTokenCredits,
        Long rewardPlanId,
        String rewardPlanName,
        Integer rewardPlanDurationDays,
        Long rewardAccessGroupId,
        String rewardAccessGroupName,
        Integer rewardAccessGroupDurationDays,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
