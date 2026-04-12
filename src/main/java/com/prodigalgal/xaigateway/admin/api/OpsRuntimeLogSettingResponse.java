package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record OpsRuntimeLogSettingResponse(
        Long id,
        String loggerName,
        String logLevel,
        boolean payloadLoggingEnabled,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
