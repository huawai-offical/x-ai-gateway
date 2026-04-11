package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public record DistributedKeyRequest(
        @NotBlank(message = "分发 key 名称不能为空。")
        String keyName,
        String description,
        Boolean active,
        List<String> allowedProtocols,
        List<String> allowedModels,
        List<String> allowedProviderTypes,
        Instant expiresAt,
        Long budgetLimitMicros,
        Integer budgetWindowSeconds,
        Integer rpmLimit,
        Integer tpmLimit,
        Integer concurrencyLimit,
        Integer stickySessionTtlSeconds,
        List<String> allowedClientFamilies,
        Boolean requireClientFamilyMatch
) {
}
