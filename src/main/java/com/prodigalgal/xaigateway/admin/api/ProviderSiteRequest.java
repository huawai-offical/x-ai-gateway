package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProviderSiteRequest(
        @NotBlank(message = "profileCode 不能为空。")
        String profileCode,
        @NotBlank(message = "displayName 不能为空。")
        String displayName,
        String vendorCode,
        String vendorName,
        @NotNull(message = "siteKind 不能为空。")
        UpstreamSiteKind siteKind,
        String baseUrlPattern,
        String description,
        Object conversationProfile,
        Boolean active
) {
    public ProviderSiteRequest(
            String profileCode,
            String displayName,
            UpstreamSiteKind siteKind,
            String baseUrlPattern,
            String description,
            Boolean active) {
        this(profileCode, displayName, null, null, siteKind, baseUrlPattern, description, null, active);
    }
}
