package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import jakarta.validation.constraints.NotBlank;

public record ModelAliasRuleRequest(
        @NotBlank(message = "协议不能为空。")
        String protocol,
        @NotBlank(message = "目标模型不能为空。")
        String targetModelName,
        ProviderType providerType,
        String baseUrlPattern,
        Integer priority,
        Boolean enabled,
        String description
) {
}
