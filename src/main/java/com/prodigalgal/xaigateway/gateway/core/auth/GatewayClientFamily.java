package com.prodigalgal.xaigateway.gateway.core.auth;

import java.util.Locale;

public enum GatewayClientFamily {
    GENERIC_OPENAI,
    CODEX,
    GEMINI_CLI,
    CLAUDE_CODE,
    OPENCODE,
    OPENCLAW,
    CURSOR,
    WINDSURF,
    KIRO,
    GITHUB_COPILOT,
    ANTHROPIC_COMPATIBLE,
    GEMINI_COMPATIBLE;

    public static GatewayClientFamily from(String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERIC_OPENAI;
        }
        String normalized = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_');
        if ("OPEN_CODE".equals(normalized)) {
            return OPENCODE;
        }
        if ("OPEN_CLAW".equals(normalized)) {
            return OPENCLAW;
        }
        if ("CLAUDE".equals(normalized) || "ANTHROPIC".equals(normalized)) {
            return ANTHROPIC_COMPATIBLE;
        }
        if ("GEMINI".equals(normalized) || "GOOGLE_GENAI".equals(normalized)) {
            return GEMINI_COMPATIBLE;
        }
        if ("COPILOT".equals(normalized) || "GITHUB".equals(normalized)) {
            return GITHUB_COPILOT;
        }
        return GatewayClientFamily.valueOf(normalized);
    }
}
