package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record ClientInstanceConfigResponse(
        Long clientInstanceId,
        String instanceId,
        String clientFamily,
        String workspaceHint,
        String format,
        String baseUrl,
        String config,
        String pluginMessageJson,
        Instant consumedAt,
        String warning
) {
}
