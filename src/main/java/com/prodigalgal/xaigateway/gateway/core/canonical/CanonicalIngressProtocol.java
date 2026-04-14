package com.prodigalgal.xaigateway.gateway.core.canonical;

import java.util.Locale;

public enum CanonicalIngressProtocol {
    OPENAI,
    RESPONSES,
    ANTHROPIC_NATIVE,
    GOOGLE_NATIVE,
    UNKNOWN;

    public static CanonicalIngressProtocol from(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            return OPENAI;
        }
        return switch (protocol.trim().toLowerCase(Locale.ROOT)) {
            case "openai" -> OPENAI;
            case "responses" -> RESPONSES;
            case "anthropic_native" -> ANTHROPIC_NATIVE;
            case "google_native" -> GOOGLE_NATIVE;
            default -> UNKNOWN;
        };
    }
}
