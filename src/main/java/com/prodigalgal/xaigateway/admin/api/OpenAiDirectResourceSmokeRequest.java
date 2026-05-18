package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record OpenAiDirectResourceSmokeRequest(
        Boolean dryRun,
        String baseUrl,
        Integer timeoutSeconds,
        String organization,
        String project,
        List<String> resourceFamilies,
        Boolean allowBillableProbes,
        Boolean allowWriteProbes
) {
    public OpenAiDirectResourceSmokeRequest(
            Boolean dryRun,
            String baseUrl,
            Integer timeoutSeconds,
            String organization,
            String project,
            List<String> resourceFamilies) {
        this(dryRun, baseUrl, timeoutSeconds, organization, project, resourceFamilies, false, false);
    }
}
