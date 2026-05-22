package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record ModelPolicyRequest(
        @NotNull(message = "scopeType 不能为空。")
        ModelPolicyScopeType scopeType,
        Long scopeId,
        String scopeRef,
        String policyKind,
        @NotBlank(message = "publicModel 不能为空。")
        String publicModel,
        String upstreamModel,
        String modelFamily,
        List<String> supportedProtocols,
        Boolean enabled,
        Boolean deny,
        Integer priority,
        Integer weight,
        Map<String, Object> capability,
        Map<String, Object> requestOverrides,
        Map<String, Object> responseOverrides,
        Map<String, Object> runtimePolicy,
        String mappingSource,
        String description
) {
}
