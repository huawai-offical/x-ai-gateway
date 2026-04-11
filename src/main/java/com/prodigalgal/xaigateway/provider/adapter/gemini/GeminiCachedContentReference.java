package com.prodigalgal.xaigateway.provider.adapter.gemini;

public record GeminiCachedContentReference(
        Long credentialId,
        String cachedContentName
) {
}
