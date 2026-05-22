package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record AccessGroupResolvedPolicyResponse(
        List<String> sourceAccessGroups,
        List<String> allowedProtocolSuites,
        List<String> allowedModels,
        List<String> allowedProviderTypes,
        List<String> allowedClientFamilies,
        Integer rpmLimit,
        Integer tpmLimit,
        Integer concurrencyLimit,
        Long dailyTokenLimit
) {
}
