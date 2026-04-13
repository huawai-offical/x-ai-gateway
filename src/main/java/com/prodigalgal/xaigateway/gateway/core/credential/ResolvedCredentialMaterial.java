package com.prodigalgal.xaigateway.gateway.core.credential;

import java.util.Map;

public record ResolvedCredentialMaterial(
        Long credentialId,
        Long siteProfileId,
        CredentialAuthKind authKind,
        String secret,
        String secretFingerprint,
        Map<String, Object> metadata,
        Long accountId,
        String source
) {
    public ResolvedCredentialMaterial {
        authKind = CredentialAuthKind.defaultValue(authKind);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        source = source == null || source.isBlank() ? "credential" : source;
    }

    public String metadataString(String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public String projectId() {
        return metadataString("projectId");
    }

    public String location() {
        return metadataString("location");
    }

    public boolean isGoogleAccessToken() {
        return authKind == CredentialAuthKind.GOOGLE_ACCESS_TOKEN;
    }
}
