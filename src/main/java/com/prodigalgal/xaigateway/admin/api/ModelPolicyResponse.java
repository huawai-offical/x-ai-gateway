package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyScopeType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ModelPolicyResponse(
        Long id,
        ModelPolicyScopeType scopeType,
        Long scopeId,
        String scopeRef,
        String policyKind,
        String publicModel,
        String publicModelKey,
        String upstreamModel,
        String upstreamModelKey,
        String modelFamily,
        List<String> supportedProtocols,
        boolean enabled,
        boolean deny,
        int priority,
        int weight,
        Map<String, Object> capability,
        Map<String, Object> requestOverrides,
        Map<String, Object> responseOverrides,
        Map<String, Object> runtimePolicy,
        String mappingSource,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
