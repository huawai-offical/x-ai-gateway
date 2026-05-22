package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record AccessGroupResponse(
        Long id,
        String groupName,
        String description,
        boolean active,
        int priority,
        List<String> allowedProtocolSuites,
        List<String> allowedModels,
        List<String> allowedProviderTypes,
        List<String> allowedClientFamilies,
        Integer rpmLimit,
        Integer tpmLimit,
        Integer concurrencyLimit,
        Long dailyTokenLimit,
        long planBindingCount,
        long keyGrantCount,
        List<AccessGroupPlanBindingResponse> planBindings,
        List<AccessGroupKeyGrantResponse> keyGrants,
        Instant createdAt,
        Instant updatedAt
) {
}
