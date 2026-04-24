package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record SubscriptionPlanRequest(
        @NotBlank(message = "套餐名称不能为空。")
        String planName,
        String description,
        Boolean active,
        @Positive(message = "默认时长必须大于 0。")
        Integer defaultDurationDays,
        @Positive(message = "最大 key 数必须大于 0。")
        Integer maxActiveKeys,
        @Positive(message = "RPM 必须大于 0。")
        Integer rpmLimit,
        @Positive(message = "TPM 必须大于 0。")
        Integer tpmLimit,
        @Positive(message = "并发上限必须大于 0。")
        Integer concurrencyLimit,
        @Positive(message = "日 token 上限必须大于 0。")
        Long dailyTokenLimit
) {
}
