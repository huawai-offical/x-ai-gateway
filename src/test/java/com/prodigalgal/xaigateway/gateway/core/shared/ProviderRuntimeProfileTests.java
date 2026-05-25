package com.prodigalgal.xaigateway.gateway.core.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRuntimeProfileTests {

    @Test
    void shouldIdentifyHeadProviderRuntimeProfilesFromSiteKindAndBaseUrl() {
        ProviderRuntimeProfile mimo = ProviderRuntimeProfile.of(
                ProviderType.OPENAI_COMPATIBLE,
                UpstreamSiteKind.XIAOMI_MIMO,
                "xiaomi_mimo",
                "https://token-plan-sgp.xiaomimimo.com/v1"
        );
        assertEquals("XIAOMI_MIMO", mimo.key());
        assertEquals(UpstreamSiteKind.XIAOMI_MIMO, mimo.siteKind());
        assertEquals("xiaomi_mimo.openai_compatible", mimo.protocolSuite());
        assertTrue(mimo.providerSpecific());

        ProviderRuntimeProfile deepSeek = ProviderRuntimeProfile.of(
                ProviderType.OPENAI_COMPATIBLE,
                null,
                null,
                "https://api.deepseek.com"
        );
        assertEquals("DEEPSEEK", deepSeek.key());
        assertEquals(UpstreamSiteKind.DEEPSEEK, deepSeek.siteKind());

        ProviderRuntimeProfile xai = ProviderRuntimeProfile.of(
                ProviderType.OPENAI_COMPATIBLE,
                null,
                null,
                "https://api.x.ai/v1"
        );
        assertEquals("XAI", xai.key());
        assertEquals(UpstreamSiteKind.GROK, xai.siteKind());
    }

    @Test
    void shouldKeepUnknownCompatibleEndpointAsExplicitGenericProfile() {
        ProviderRuntimeProfile profile = ProviderRuntimeProfile.of(
                ProviderType.OPENAI_COMPATIBLE,
                null,
                null,
                "https://compatible.example.com/v1"
        );

        assertEquals("OPENAI_COMPATIBLE_GENERIC", profile.key());
        assertEquals(UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, profile.siteKind());
        assertFalse(profile.providerSpecific());
    }
}
