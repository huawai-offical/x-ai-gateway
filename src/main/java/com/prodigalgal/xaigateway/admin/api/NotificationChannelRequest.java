package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;

public record NotificationChannelRequest(
        @NotBlank String channelName,
        @NotBlank String channelType,
        Long webhookEndpointId,
        String emailTo,
        String templateMode,
        Boolean enabled
) {
}
