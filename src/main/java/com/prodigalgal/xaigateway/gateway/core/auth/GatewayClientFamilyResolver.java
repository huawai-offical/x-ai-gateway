package com.prodigalgal.xaigateway.gateway.core.auth;

import org.springframework.stereotype.Component;

@Component
public class GatewayClientFamilyResolver {

    public GatewayClientFamily resolve(String explicitFamily, String userAgent) {
        if (explicitFamily != null && !explicitFamily.isBlank()) {
            return GatewayClientFamily.from(explicitFamily);
        }
        if (userAgent == null || userAgent.isBlank()) {
            return GatewayClientFamily.GENERIC_OPENAI;
        }

        String normalized = userAgent.toLowerCase();
        if (normalized.contains("openclaw") || normalized.contains("open-claw")) {
            return GatewayClientFamily.OPENCLAW;
        }
        if (normalized.contains("opencode") || normalized.contains("open-code")) {
            return GatewayClientFamily.OPENCODE;
        }
        if (normalized.contains("cursor")) {
            return GatewayClientFamily.CURSOR;
        }
        if (normalized.contains("windsurf")) {
            return GatewayClientFamily.WINDSURF;
        }
        if (normalized.contains("kiro")) {
            return GatewayClientFamily.KIRO;
        }
        if (normalized.contains("copilot")) {
            return GatewayClientFamily.GITHUB_COPILOT;
        }
        if (normalized.contains("codex")) {
            return GatewayClientFamily.CODEX;
        }
        if (normalized.contains("gemini")) {
            return GatewayClientFamily.GEMINI_CLI;
        }
        if (normalized.contains("claude")) {
            return GatewayClientFamily.CLAUDE_CODE;
        }
        return GatewayClientFamily.GENERIC_OPENAI;
    }
}
