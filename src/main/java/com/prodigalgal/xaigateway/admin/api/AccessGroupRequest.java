package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AccessGroupRequest(
        @NotBlank(message = "访问组名称不能为空。")
        String groupName,
        String description,
        Boolean active,
        Integer priority,
        List<String> allowedProtocols,
        List<String> allowedModels,
        List<String> allowedProviderTypes,
        List<String> allowedClientFamilies,
        Integer rpmLimit,
        Integer tpmLimit,
        Integer concurrencyLimit,
        Long dailyTokenLimit
) {
}
