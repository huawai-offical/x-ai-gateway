package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotNull;

public record AccessGroupPlanBindingRequest(
        @NotNull(message = "套餐 ID 不能为空。")
        Long planId,
        Boolean active,
        Integer priority
) {
}
