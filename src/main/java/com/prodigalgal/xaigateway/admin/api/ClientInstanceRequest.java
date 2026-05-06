package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClientInstanceRequest(
        @NotNull Long distributedKeyId,
        @NotBlank String clientFamily,
        @NotBlank String instanceId,
        String displayName,
        String workspaceHint,
        String pluginName,
        String pluginVersion,
        String deepLinkScheme,
        String metadataJson,
        Boolean active
) {
}
