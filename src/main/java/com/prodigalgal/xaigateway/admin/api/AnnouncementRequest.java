package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record AnnouncementRequest(
        @NotBlank(message = "公告标题不能为空。")
        String title,
        String summary,
        String body,
        String status,
        String audienceType,
        Long audienceUserId,
        Long audiencePlanId,
        Instant publishedAt,
        Instant expiresAt
) {
}
