package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import java.util.List;

public enum OfficialAccountType {
    CODEX(
            UpstreamAccountProviderType.CODEX_OAUTH,
            "CODEX",
            "codex",
            List.of("gpt-4.1", "o4-mini"),
            "OFFICIAL",
            86_400,
            2_000_000L,
            300L
    ),
    GITHUB_COPILOT(
            UpstreamAccountProviderType.COPILOT_OAUTH,
            "GITHUB_COPILOT",
            "github_copilot",
            List.of("gpt-4.1", "claude-3.7-sonnet"),
            "OFFICIAL",
            86_400,
            1_000_000L,
            200L
    ),
    GEMINI_CLI(
            UpstreamAccountProviderType.GEMINI_OAUTH,
            "GEMINI_CLI",
            "gemini_cli",
            List.of("gemini-2.5-pro", "gemini-2.5-flash"),
            "OFFICIAL",
            86_400,
            1_000_000L,
            100L
    );

    private final UpstreamAccountProviderType providerType;
    private final String clientFamily;
    private final String externalPrefix;
    private final List<String> defaultModels;
    private final String defaultPlanTier;
    private final int defaultQuotaWindowSeconds;
    private final long defaultQuotaRemainingTokens;
    private final long defaultQuotaRemainingRequests;

    OfficialAccountType(
            UpstreamAccountProviderType providerType,
            String clientFamily,
            String externalPrefix,
            List<String> defaultModels,
            String defaultPlanTier,
            int defaultQuotaWindowSeconds,
            long defaultQuotaRemainingTokens,
            long defaultQuotaRemainingRequests) {
        this.providerType = providerType;
        this.clientFamily = clientFamily;
        this.externalPrefix = externalPrefix;
        this.defaultModels = defaultModels;
        this.defaultPlanTier = defaultPlanTier;
        this.defaultQuotaWindowSeconds = defaultQuotaWindowSeconds;
        this.defaultQuotaRemainingTokens = defaultQuotaRemainingTokens;
        this.defaultQuotaRemainingRequests = defaultQuotaRemainingRequests;
    }

    public UpstreamAccountProviderType providerType() {
        return providerType;
    }

    public String clientFamily() {
        return clientFamily;
    }

    public String externalPrefix() {
        return externalPrefix;
    }

    public List<String> defaultModels() {
        return defaultModels;
    }

    public String defaultPlanTier() {
        return defaultPlanTier;
    }

    public int defaultQuotaWindowSeconds() {
        return defaultQuotaWindowSeconds;
    }

    public long defaultQuotaRemainingTokens() {
        return defaultQuotaRemainingTokens;
    }

    public long defaultQuotaRemainingRequests() {
        return defaultQuotaRemainingRequests;
    }
}
