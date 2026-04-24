package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record PromoCampaignRequest(
        @NotBlank(message = "活动名称不能为空。")
        String campaignName,
        String description,
        Boolean active,
        Long rewardTokenCredits,
        Integer maxRedemptionsPerUser,
        Instant startsAt,
        Instant expiresAt
) {
}
