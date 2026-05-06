package com.prodigalgal.xaigateway.gateway.core.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayClientFamilyResolverTests {

    private final GatewayClientFamilyResolver resolver = new GatewayClientFamilyResolver();

    @Test
    void shouldNormalizeCloudCliFamiliesAndAliases() {
        assertEquals(GatewayClientFamily.OPENCODE, GatewayClientFamily.from("open-code"));
        assertEquals(GatewayClientFamily.OPENCLAW, GatewayClientFamily.from("open-claw"));
        assertEquals(GatewayClientFamily.GITHUB_COPILOT, GatewayClientFamily.from("copilot"));
        assertEquals(GatewayClientFamily.ANTHROPIC_COMPATIBLE, GatewayClientFamily.from("anthropic"));
        assertEquals(GatewayClientFamily.GEMINI_COMPATIBLE, GatewayClientFamily.from("google-genai"));
    }

    @Test
    void shouldResolveKnownCliUserAgents() {
        assertEquals(GatewayClientFamily.CURSOR, resolver.resolve(null, "Cursor/1.0"));
        assertEquals(GatewayClientFamily.WINDSURF, resolver.resolve(null, "Windsurf"));
        assertEquals(GatewayClientFamily.KIRO, resolver.resolve(null, "Kiro"));
        assertEquals(GatewayClientFamily.OPENCODE, resolver.resolve(null, "open-code cli"));
        assertEquals(GatewayClientFamily.OPENCLAW, resolver.resolve(null, "OpenClaw"));
    }
}
