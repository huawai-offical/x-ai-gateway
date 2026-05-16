package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ProviderReferenceGapService;
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

@WebFluxTest(controllers = ProviderReferenceGapAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class ProviderReferenceGapAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ProviderReferenceGapService providerReferenceGapService;

    @Test
    void shouldReturnProviderReferenceGapMatrix() {
        Mockito.when(providerReferenceGapService.get()).thenReturn(new ProviderReferenceGapResponse(
                "new-api relay/channel",
                "test",
                "catalog",
                "classpath",
                Instant.parse("2026-05-13T00:00:00Z"),
                List.of(new ProviderReferenceGapRow(
                        "openai",
                        "openai",
                        "OpenAI",
                        "SUPPORTED",
                        "native-first",
                        "openai-native",
                        "catalog + capability matrix",
                        List.of("chat"),
                        List.of(),
                        "ok"
                )),
                List.of(new ProviderMediaCapabilityRow(
                        "rerank",
                        "/v1/rerank",
                        "SUPPORTED_GOVERNED",
                        List.of("cohere", "jina"),
                        "dedicated rerank",
                        "mock"
                )),
                List.of(new ProviderPricingSyncStatusRow(
                        "gemini",
                        "Gemini",
                        "public",
                        "checksum",
                        "PUBLIC_SOURCE_TRACKED",
                        Instant.parse("2026-05-13T00:00:00Z"),
                        "catalog:gemini:abc123",
                        "abc123",
                        "PUBLIC_PRICE_PAGE",
                        "APPROVED",
                        Instant.parse("2026-05-13T00:00:00Z"),
                        null,
                        "NO_DRIFT",
                        true,
                        "mock-smoke + optional-real-key",
                        List.of("AUTHENTICATION_FAILED", "QUOTA_EXCEEDED"),
                        true,
                        "notes"
                )),
                List.of("next")
        ));

        webTestClient.get()
                .uri("/admin/provider-reference-gap")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.referenceName").isEqualTo("new-api relay/channel")
                .jsonPath("$.providers[0].referenceChannel").isEqualTo("openai")
                .jsonPath("$.mediaCapabilities[0].capability").isEqualTo("rerank")
                .jsonPath("$.pricingSync[0].providerCode").isEqualTo("gemini")
                .jsonPath("$.pricingSync[0].approvalStatus").isEqualTo("APPROVED")
                .jsonPath("$.pricingSync[0].productionEligible").isEqualTo(true)
                .jsonPath("$.pricingSync[0].requiresRealKey").isEqualTo(true);
    }
}
