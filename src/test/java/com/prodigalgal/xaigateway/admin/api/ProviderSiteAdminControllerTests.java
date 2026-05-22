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
import java.util.Map;
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

    @Test
    void shouldExposeProviderSiteManagementEndpoints() {
        Mockito.when(providerSiteAdminService.get(1L)).thenReturn(sampleSite());
        Mockito.when(providerSiteAdminService.create(Mockito.any(ProviderSiteRequest.class))).thenReturn(sampleSite());
        Mockito.when(providerSiteAdminService.update(Mockito.eq(1L), Mockito.any(ProviderSiteRequest.class))).thenReturn(sampleSite());
        Mockito.when(providerSiteAdminService.refreshCapabilities(1L)).thenReturn(sampleSite());
        Mockito.when(providerSiteAdminService.refreshCapabilities(List.of(1L))).thenReturn(List.of(sampleSite()));
        Mockito.when(providerSiteAdminService.capabilityMatrix()).thenReturn(List.of(sampleCapabilityMatrixRow()));
        Mockito.when(providerSiteAdminService.listPresets()).thenReturn(List.of(samplePreset()));
        Mockito.when(providerSiteAdminService.getPreset("openai-main")).thenReturn(samplePreset());
        Mockito.when(providerSiteAdminService.importPreset(Mockito.eq("openai-main"), Mockito.any(ProviderSitePresetImportRequest.class)))
                .thenReturn(sampleSite());

        webTestClient.get()
                .uri("/admin/provider-sites/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1);

        webTestClient.post()
                .uri("/admin/provider-sites")
                .bodyValue(sampleRequest())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.profileCode").isEqualTo("site:openai_direct");

        webTestClient.put()
                .uri("/admin/provider-sites/1")
                .bodyValue(sampleRequest())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.displayName").isEqualTo("OPENAI_DIRECT");

        webTestClient.delete()
                .uri("/admin/provider-sites/1")
                .exchange()
                .expectStatus().isOk();
        Mockito.verify(providerSiteAdminService).delete(1L);

        webTestClient.post()
                .uri("/admin/provider-sites/1/refresh")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.healthState").isEqualTo("READY");

        webTestClient.post()
                .uri("/admin/provider-sites/refresh")
                .bodyValue(new ProviderSiteRefreshRequest(List.of(1L)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(1);

        webTestClient.get()
                .uri("/admin/provider-sites/capability-matrix")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].siteProfileId").isEqualTo(1);

        webTestClient.get()
                .uri("/admin/provider-sites/presets")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].code").isEqualTo("openai-main");

        webTestClient.get()
                .uri("/admin/provider-sites/presets/openai-main")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.displayName").isEqualTo("OpenAI 主站");

        webTestClient.post()
                .uri("/admin/provider-sites/presets/openai-main/import")
                .bodyValue(new ProviderSitePresetImportRequest(true, true))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.profileCode").isEqualTo("site:openai_direct");
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

    private ProviderSiteRequest sampleRequest() {
        return new ProviderSiteRequest(
                "site:openai_direct",
                "OpenAI 主站",
                "openai",
                "OpenAI",
                UpstreamSiteKind.OPENAI_DIRECT,
                "https://api.openai.com",
                "sample",
                Map.of("reasoningContentMode", "passthrough"),
                true
        );
    }

    private ProviderSitePresetResponse samplePreset() {
        return new ProviderSitePresetResponse(
                "openai-main",
                "site:openai_direct",
                "OpenAI 主站",
                "openai",
                "OpenAI",
                UpstreamSiteKind.OPENAI_DIRECT,
                ProviderFamily.OPENAI,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ModelAddressingStrategy.MODEL_NAME,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://api.openai.com",
                "sample",
                List.of("openai", "responses"),
                "sse",
                "provider-native",
                List.of("chat", "responses"),
                "official",
                "openai",
                "2026-05-22",
                "local",
                false,
                List.of("models"),
                "openai",
                "native",
                List.of("gpt"),
                "{}",
                List.of(),
                Map.of("reasoningContentMode", "passthrough"),
                Map.of(),
                false,
                null
        );
    }

    private CapabilityMatrixRowResponse sampleCapabilityMatrixRow() {
        return new CapabilityMatrixRowResponse(
                1L,
                "site:openai_direct",
                "OPENAI_DIRECT",
                ProviderFamily.OPENAI,
                UpstreamSiteKind.OPENAI_DIRECT,
                SiteProfileSource.MANUAL,
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
                0,
                null,
                1L,
                true,
                2,
                Instant.now(),
                Map.of(),
                Map.of(),
                true,
                true,
                false,
                false,
                true,
                true,
                true
        );
    }
}
