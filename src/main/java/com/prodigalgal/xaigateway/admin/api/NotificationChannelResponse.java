package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record NotificationChannelResponse(
        Long id,
        String channelName,
        String channelType,
        Long webhookEndpointId,
        String emailTo,
        String templateMode,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
