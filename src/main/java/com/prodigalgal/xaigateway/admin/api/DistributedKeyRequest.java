package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record DistributedKeyRequest(
        @NotBlank(message = "分发 key 名称不能为空。")
        String keyName,
        String description,
        Boolean active,
        List<String> allowedProtocols,
        List<String> allowedModels
) {
}
