package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminService;
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
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
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
                .jsonPath("$[0].supportsRealtime").isEqualTo(true);
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
                2,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
    }
}
