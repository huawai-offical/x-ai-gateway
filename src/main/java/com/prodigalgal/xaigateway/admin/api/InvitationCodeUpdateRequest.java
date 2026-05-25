package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record InvitationCodeUpdateRequest(
        Boolean active,
        Integer maxUses,
        Instant expiresAt,
        Long ownerUserId,
        Long rewardTokenCredits,
        Long referrerRewardTokenCredits,
        Long rewardPlanId,
        Integer rewardPlanDurationDays,
        Long rewardAccessGroupId,
        Integer rewardAccessGroupDurationDays,
        String notes
) {
}
