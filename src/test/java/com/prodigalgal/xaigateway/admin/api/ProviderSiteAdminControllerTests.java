package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminService;
import com.prodigalgal.xaigateway.gateway.core.catalog.SurfaceCapabilityView;
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionView;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = {ProviderSiteAdminController.class, CapabilityMatrixAdminController.class})
@Import(PermitAllSecurityTestConfig.class)
class ProviderSiteAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ProviderSiteAdminService providerSiteAdminService;

    @Test
    void shouldListProviderSites() {
        Mockito.when(providerSiteAdminService.list()).thenReturn(List.of(sampleSite()));

        webTestClient.get()
                .uri("/admin/provider-sites")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].profileCode").isEqualTo("site:openai_direct")
                .jsonPath("$[0].providerFamily").isEqualTo("OPENAI");
    }

    @Test
    void shouldExposeCapabilityMatrix() {
        Mockito.when(providerSiteAdminService.capabilityMatrix()).thenReturn(List.of(
                new CapabilityMatrixRowResponse(
                        1L,
                        "site:openai_direct",
                        "OPENAI_DIRECT",
                        ProviderFamily.OPENAI,
                        UpstreamSiteKind.OPENAI_DIRECT,
                        AuthStrategy.BEARER,
                        PathStrategy.OPENAI_V1,
                        ErrorSchemaStrategy.OPENAI_ERROR,
                        "READY",
                        null,
                        List.of("openai", "responses"),
                        "openai",
                        List.of("api_key"),
                        "sse",
                        "provider-native",
                        1,
                        Instant.parse("2026-04-13T03:00:00Z"),
                        java.util.Map.of(
                                "response_object",
                                new CapabilityResolutionView("emulated", "emulated", "emulated", List.of(), List.of())
                        ),
                        java.util.Map.of(
                                "response_create",
                                new SurfaceCapabilityView(
                                        TranslationResourceType.RESPONSE,
                                        TranslationOperation.RESPONSE_CREATE,
                                        InteropCapabilityLevel.EMULATED,
                                        InteropCapabilityLevel.EMULATED,
                                        InteropCapabilityLevel.EMULATED,
                                        List.of("response_object"),
                                        java.util.Map.of(
                                                "response_object",
                                                new CapabilityResolutionView("emulated", "emulated", "emulated", List.of(), List.of())
                                        )
                                )
                        ),
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true
                )
        ));

        webTestClient.get()
                .uri("/admin/capability-matrix")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].supportsResponses").isEqualTo(true)
                .jsonPath("$[0].streamTransport").isEqualTo("sse")
                .jsonPath("$[0].fallbackStrategy").isEqualTo("provider-native")
                .jsonPath("$[0].cooldownCredentialCount").isEqualTo(1)
                .jsonPath("$[0].features.response_object.effectiveLevel").isEqualTo("emulated")
                .jsonPath("$[0].surfaces.response_create.overallCapabilityLevel").isEqualTo("EMULATED")
                .jsonPath("$[0].supportsRealtime").isEqualTo(true);
    }

    @Test
    void shouldRefreshSelectedProviderSites() {
        Mockito.when(providerSiteAdminService.refreshCapabilities(List.of(1L, 2L)))
                .thenReturn(List.of(sampleSite()));

        webTestClient.post()
                .uri("/admin/provider-sites/refresh-capabilities")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"siteProfileIds":[1,2]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].profileCode").isEqualTo("site:openai_direct")
                .jsonPath("$[0].cooldownCredentialCount").isEqualTo(1);
    }

    private ProviderSiteResponse sampleSite() {
        return new ProviderSiteResponse(
                1L,
                "site:openai_direct",
                "OPENAI_DIRECT",
                ProviderFamily.OPENAI,
                UpstreamSiteKind.OPENAI_DIRECT,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ModelAddressingStrategy.MODEL_NAME,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://api.openai.com",
                "sample",
                true,
                "READY",
                null,
                List.of("openai", "responses"),
                "openai",
                List.of("api_key"),
                "sse",
                "provider-native",
                1,
                Instant.parse("2026-04-13T03:00:00Z"),
                java.util.Map.of(
                        "response_object",
                        new CapabilityResolutionView("emulated", "emulated", "emulated", List.of(), List.of())
                ),
                java.util.Map.of(
                        "response_create",
                        new SurfaceCapabilityView(
                                TranslationResourceType.RESPONSE,
                                TranslationOperation.RESPONSE_CREATE,
                                InteropCapabilityLevel.EMULATED,
                                InteropCapabilityLevel.EMULATED,
                                InteropCapabilityLevel.EMULATED,
                                List.of("response_object"),
                                java.util.Map.of(
                                        "response_object",
                                        new CapabilityResolutionView("emulated", "emulated", "emulated", List.of(), List.of())
                                )
                        )
                ),
                2,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
    }
}
