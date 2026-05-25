package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record InvitationCodeBatchRequest(
        List<String> codes,
        String rawText,
        Integer generateCount,
        String prefix,
        Integer maxUses,
        Boolean active,
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
