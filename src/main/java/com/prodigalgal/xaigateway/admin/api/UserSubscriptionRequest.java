package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record UserSubscriptionRequest(
        @NotNull(message = "用户 ID 不能为空。")
        @Positive(message = "用户 ID 必须大于 0。")
        Long userId,
        @NotNull(message = "套餐 ID 不能为空。")
        @Positive(message = "套餐 ID 必须大于 0。")
        Long planId,
        String status,
        Instant startsAt,
        Instant expiresAt,
        Boolean autoRenew,
        String notes
) {
}
