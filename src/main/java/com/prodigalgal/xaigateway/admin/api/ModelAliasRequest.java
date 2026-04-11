package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ModelAliasRequest(
        @NotBlank(message = "别名名称不能为空。")
        String aliasName,
        Boolean enabled,
        String description,
        @Valid
        List<ModelAliasRuleRequest> rules
) {
}
