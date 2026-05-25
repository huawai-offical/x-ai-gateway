package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record InvitationCodeUsageResponse(
        Long id,
        Long invitationCodeId,
        String code,
        Long userId,
        String registrationEmail,
        String registrationChannel,
        String requestSource,
        Long referrerUserId,
        String referrerEmail,
        long rewardTokenCredits,
        long referrerRewardTokenCredits,
        Long rewardPlanId,
        String rewardPlanName,
        Long rewardSubscriptionId,
        Long rewardAccessGroupId,
        String rewardAccessGroupName,
        Long rewardAccessGroupGrantId,
        Instant usedAt,
        Instant createdAt
) {
}
