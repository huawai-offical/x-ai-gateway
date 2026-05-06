package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record DistributedKeyOnboardingPackResponse(
        String keyName,
        String maskedKey,
        String baseUrl,
        String apiBaseUrl,
        String secretPolicy,
        List<DistributedKeyOnboardingSnippetResponse> clientConfigs,
        List<DistributedKeyOnboardingDeepLinkResponse> deepLinks,
        String mcpServerConfig,
        List<String> prompts,
        List<String> skills,
        List<String> smokeTests,
        List<String> troubleshooting,
        Instant generatedAt
) {
}
