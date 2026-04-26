package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotNull;

public record AccessGroupKeyGrantRequest(
        @NotNull(message = "分发 Key ID 不能为空。")
        Long distributedKeyId,
        String grantMode,
        Boolean active,
        Integer priority,
        String reason
) {
}
