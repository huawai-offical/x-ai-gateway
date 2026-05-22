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
import com.prodigalgal.xaigateway.gateway.core.shared.SiteProfileSource;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = ProviderSiteAdminController.class)
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
    void shouldExposeSiteCapabilities() {
        Mockito.when(providerSiteAdminService.listCapabilities(1L)).thenReturn(List.of(new SiteModelCapabilityResponse(
                5L,
                "gpt-4o",
                "gpt-4o",
                List.of("openai", "responses"),
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                InteropCapabilityLevel.EMULATED,
                null,
                List.of(),
                java.util.Map.of(),
                Instant.parse("2026-05-21T00:00:00Z")
        )));
        webTestClient.get()
                .uri("/admin/provider-sites/1/capabilities")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].modelKey").isEqualTo("gpt-4o")
                .jsonPath("$[0].supportedProtocols[0]").isEqualTo("openai");
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
                SiteProfileSource.MANUAL,
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
                2L,
                true,
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
