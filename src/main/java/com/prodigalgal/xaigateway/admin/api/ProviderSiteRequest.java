package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProviderSiteRequest(
        @NotBlank(message = "profileCode 不能为空。")
        String profileCode,
        @NotBlank(message = "displayName 不能为空。")
        String displayName,
        @NotNull(message = "siteKind 不能为空。")
        UpstreamSiteKind siteKind,
        String baseUrlPattern,
        String description,
        Boolean active
) {
}
