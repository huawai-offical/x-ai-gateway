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
