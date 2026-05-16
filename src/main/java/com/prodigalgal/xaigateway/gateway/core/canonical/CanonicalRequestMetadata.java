package com.prodigalgal.xaigateway.gateway.core.canonical;

public record CanonicalRequestMetadata(
        String clientFamily,
        String clientInstance,
        String workspaceHint,
        String sessionAffinitySource,
        String sessionAffinityKey,
        String openAiBeta,
        String originator,
        String userAgent,
        String openAiOrganization,
        String openAiProject,
        String idempotencyKey
) {
    public CanonicalRequestMetadata(
            String clientFamily,
            String clientInstance,
            String workspaceHint,
            String sessionAffinitySource,
            String sessionAffinityKey,
            String openAiBeta,
            String originator,
            String userAgent) {
        this(
                clientFamily,
                clientInstance,
                workspaceHint,
                sessionAffinitySource,
                sessionAffinityKey,
                openAiBeta,
                originator,
                userAgent,
                null,
                null,
                null
        );
    }
}
