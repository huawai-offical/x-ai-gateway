package com.prodigalgal.xaigateway.portal.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record PortalKeyCreateRequest(
        @NotBlank(message = "Key 名称不能为空。")
        String keyName,
        List<String> allowedProtocolSuites,
        List<String> allowedModels,
        Integer rpmLimit,
        Integer tpmLimit,
        Integer concurrencyLimit
) {
}
