package com.prodigalgal.xaigateway.gateway.core.auth;

import java.util.Locale;

public enum GatewayClientFamily {
    GENERIC_OPENAI,
    CODEX,
    GEMINI_CLI,
    CLAUDE_CODE;

    public static GatewayClientFamily from(String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERIC_OPENAI;
        }
        return GatewayClientFamily.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
